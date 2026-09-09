-- fx_filter: DFM1 filter plugin for sixolet's fx mod framework.

local fx = require("fx/lib/fx")
local mod = require 'core/mods'

local FxFilter = fx:new{
  subpath = "/fx_filter"
}

function FxFilter:add_params()
  params:add_separator("fx_filter", "fx filter")
  FxFilter:add_slot("fx_filter_slot", "slot")
  FxFilter:add_control("fx_filter_cutoff", "cutoff", "cutoff",
    controlspec.new(20, 20000, 'exp', 0, 1000, "Hz"))
  FxFilter:add_control("fx_filter_res", "resonance", "res",
    controlspec.new(0, 1.2, 'lin', 0.01, 0.1, ""))
  FxFilter:add_option("fx_filter_type", "type", "type", {"lowpass", "highpass"}, 1)
  FxFilter:add_taper("fx_filter_dfm_gain", "input gain", "dfm_gain", 0.1, 8, 1, 1, "")
  FxFilter:add_option("fx_filter_drive_mode", "drive mode", "drive_mode",
    {"off", "pre-filter", "post-filter"}, 1)
  FxFilter:add_taper("fx_filter_drive_amount", "drive amount", "drive_amount", 1, 8, 1, 1, "")
  FxFilter:add_control("fx_filter_stereo_spread", "stereo spread", "stereo_spread",
    controlspec.new(0, 1, 'lin', 0.01, 0, ""))
  FxFilter:add_control("fx_filter_noise", "noise", "noise",
    controlspec.new(0, 0.005, 'lin', 0.0001, 0.0003, ""))
  FxFilter:add_control("fx_filter_env_amount", "env > cutoff", "env_amount",
    controlspec.new(-4, 4, 'lin', 0.05, 0, "oct"))
  FxFilter:add_taper("fx_filter_env_sens", "env sensitivity", "env_sens", 0, 20, 2, 1, "")
  FxFilter:add_taper("fx_filter_env_attack", "env attack", "env_attack", 0.001, 1, 0.01, 4, "s")
  FxFilter:add_taper("fx_filter_env_release", "env release", "env_release", 0.01, 2, 0.2, 3, "s")
  FxFilter:add_option("fx_filter_lfo_shape", "lfo shape", "lfo_shape",
    {"sine", "triangle", "saw", "square", "s&h", "noise"}, 1)
  FxFilter:add_taper("fx_filter_lfo_rate", "lfo rate", "lfo_rate", 0.01, 20, 1, 3, "Hz")
  -- UI shows -127..127, SC side takes -4..4 octaves.
  params:add_control("fx_filter_lfo_depth", "lfo > cutoff",
    controlspec.new(-127, 127, 'lin', 1, 0, ""))
  params:set_action("fx_filter_lfo_depth", function(val)
    osc.send({ "localhost", 57120 }, FxFilter.subpath .. "/set", { "lfo_depth", val / 127 * 4 })
  end)
end

mod.hook.register("script_pre_init", "filter mod pre init", function()
  FxFilter:install()
end)

mod.hook.register("script_post_cleanup", "filter mod post cleanup", function()
end)

return FxFilter
