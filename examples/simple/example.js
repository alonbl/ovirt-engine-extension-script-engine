Base = Packages.org.ovirt.engine.api.extensions.Base;

function MyExtension() {}
MyExtension.prototype = {
_initialize: function(input, output) {
	input.get(Base.InvokeKeys.CONTEXT).mput(
		Base.ContextKeys.EXTENSION_NAME,
		"JavaScript example"
	).mput(
		Base.ContextKeys.LICENSE,
		"ASL 2.0"
	).mput(
		Base.ContextKeys.HOME_URL,
		"http://www.ovirt.org"
	).mput(
		Base.ContextKeys.VERSION,
		"0.0.0"
	).mput(
		Base.ContextKeys.BUILD_INTERFACE_VERSION,
		java.lang.Integer.valueOf(Base.INTERFACE_VERSION_CURRENT)
	);
},
invoke: function(input, output) {
	try {
		var command = input.get(Base.InvokeKeys.COMMAND);
		if (command == Base.InvokeCommands.INITIALIZE) {
			this._initialize(input, output);
		} else {
			output.put(
				Base.InvokeKeys.RESULT,
				java.lang.Integer.valueOf(Base.InvokeResult.UNSUPPORTED)
			);
		}
		output.put(
			Base.InvokeKeys.RESULT,
			java.lang.Integer.valueOf(Base.InvokeResult.SUCCESS)
		);
	} catch(e) {
		output.mput(
			Base.InvokeKeys.RESULT,
			java.lang.Integer.valueOf(Base.InvokeResult.FAILED)
		).mput(
			Base.InvokeKeys.MESSAGE,
			e.message
		);
	}
},
};
function create() {
	return new MyExtension();
}
