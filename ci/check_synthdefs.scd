// Run by ci/check_sc.sh inside a headless sclang.
// Builds every model's SynthDef without a server and checks that each
// param key in FxFilter's defaults exists as a control in each def.
// Prints ALL_OK and exits 0 on success, exits 1 otherwise.
(
var ok = true;
var plugin, keys;

if (\FxFilter.asClass.isNil) {
    "FAIL: FxFilter class did not compile".postln;
    1.exit;
};

try {
    plugin = FxFilter.new;
    plugin.addSynthdefs;
} { |err|
    "FAIL: addSynthdefs threw: %".format(err.errorString).postln;
    ok = false;
};

if (ok) {
    keys = plugin.params.keys.asArray.sort;
    FxFilter.modelSymbols.do { |sym|
        var desc = SynthDescLib.global.at(sym);
        if (desc.isNil) {
            "FAIL: SynthDef % not built".format(sym).postln;
            ok = false;
        } {
            var names = desc.controlNames;
            var missing = keys.reject { |k| names.includes(k) };
            if (missing.notEmpty) {
                "FAIL: % lacks controls %".format(sym, missing).postln;
                ok = false;
            } {
                "ok: % (% controls)".format(sym, names.size).postln;
            };
        };
    };
};

if (ok) { "ALL_OK".postln; 0.exit } { 1.exit };
)
