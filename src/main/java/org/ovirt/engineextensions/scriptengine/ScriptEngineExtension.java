/*
 * Copyright 2014 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package org.ovirt.engineextensions.scriptengine;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.ovirt.engine.api.extensions.Base;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.api.extensions.Extension;

public class ScriptEngineExtension implements Extension {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(?<var>[^}]*)\\}");

    private static final Logger log = LoggerFactory.getLogger(ScriptEngineExtension.class);

    private final String CONFIG_ENGINE = "config.scriptengine.engine";
    private final String CONFIG_SCRIPT_PREFIX = "config.scriptengine.script.";
    private final String CONFIG_FUNCTION = "config.scriptengine.function";

    private Extension proxied;

    private static String expandProperties(Properties props, String s) {
        StringBuilder ret = new StringBuilder();
        Matcher m = VAR_PATTERN.matcher(s);
        int last = 0;
        while (m.find()) {
            ret.append(s.substring(last, m.start()));
            ret.append(props.getProperty(m.group("var")));
            last = m.end();
        }
        ret.append(s.substring(last, m.regionEnd()));
        return ret.toString();
    }

    public static List<String> getPropertiesByPrefix(Properties props, String prefix) {
        List<String> keys = new LinkedList<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        Collections.sort(keys);
        return keys;
    }

    private Extension load(Properties props) throws Exception {

        log.info(
            String.format(
                "%s-%s (%s)",
                Config.PACKAGE_NAME,
                Config.PACKAGE_VERSION,
                Config.PACKAGE_DISPLAY_NAME
            )
        );

        final String engineName = props.getProperty(CONFIG_ENGINE, "JavaScript");

        ScriptEngine engine = new ScriptEngineManager().getEngineByName(engineName);
        if (engine == null) {
            throw new IllegalStateException(String.format(
                "Script engine '%1$s' cannot be loaded.",
                engineName
            ));
        }

        engine.setContext(new SimpleScriptContext());

        for (String key : getPropertiesByPrefix(props, CONFIG_SCRIPT_PREFIX)) {
            String script = expandProperties(System.getProperties(), props.getProperty(key));

            if (log.isDebugEnabled()) {
                log.debug(String.format("loading: %s:%s", engineName, script));
            }

            try(
                InputStream in = new FileInputStream(script);
                Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            ) {
                engine.put(ScriptEngine.FILENAME, script);
                engine.eval(reader);
                if (!Invocable.class.isAssignableFrom(engine.getClass())) {
                    throw new IllegalStateException(String.format(
                        "Script engine '%1$s' does not support Invocable interface.",
                        engineName
                    ));
                }
            }
        }

        Invocable invocable = (Invocable)engine;
        Object extensionImpl = invocable.invokeFunction(props.getProperty(CONFIG_FUNCTION, "create"));
        if (extensionImpl == null) {
            throw new IllegalStateException("Cannot initialize extension instance, create returned null.");
        }
        return invocable.getInterface(extensionImpl, Extension.class);
    }

    @Override
    public void invoke(ExtMap input, ExtMap output) {
        try {
            log.debug("enter");
            if (input.get(Base.InvokeKeys.COMMAND).equals(Base.InvokeCommands.LOAD)) {
                proxied = load(input.<ExtMap>get(Base.InvokeKeys.CONTEXT).<Properties>get(Base.ContextKeys.CONFIGURATION));
            }

            if (proxied != null) {
                proxied.invoke(input, output);
            }
            log.debug("leave");
        } catch (Exception e) {
            log.error(String.format("Unexpected exception %s: %s", e.getClass(), e.getMessage()));
            log.debug("Exception", e);
            output.mput(
                Base.InvokeKeys.RESULT,
                Base.InvokeResult.FAILED
            ).mput(
                Base.InvokeKeys.MESSAGE,
                e.getMessage()
            );
        }
    }

}
