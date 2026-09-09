-- lib/mod.lua
-- fx_filter integration for sixolet's fx mod framework

local fx = require("fx/lib/fx")
local mod = {}

-- Define default parameters and control specifications
local params_def = {
  { id = "cutoff",        name = "Cutoff",        spec = controlspec.new(20, 20000, 'exp', 0, 1000, "Hz") },
  { id = "res",           name = "Resonance",     spec = controlspec.new(0, 1.2, 'lin', 0.01, 0.1, "") },
  { id = "type",          name = "Filter Type",   type = "option", options = { "Lowpass", "Highpass" }, default = 1 },
  { id = "dfm_gain",      name = "DFM Input Gain",spec = controlspec.new(0.1, 8.0, 'lin', 0.05, 1.0, "") },
  { id = "drive_mode",    name = "Tanh Drive Mode",type = "option", options = { "Off", "Pre-Filter", "Post-Filter" }, default = 1 },
  { id = "drive_amount",  name = "Tanh Drive Amt",spec = controlspec.new(1.0, 8.0, 'lin', 0.05, 1.0, "") },
  { id = "stereo_spread", name = "Stereo Spread", spec = controlspec.new(0.0, 1.0, 'lin', 0.01, 0.0, "") },
  { id = "noise",         name = "Noise Level",   spec = controlspec.new(0.0, 0.005, 'lin', 0.0001, 0.0003, "") },
  { id = "mix",           name = "Dry / Wet",     spec = controlspec.new(0.0, 1.0, 'lin', 0.01, 1.0, "") },
}

function mod.init()
  local node = fx.add_node("fx_filter", "FX Filter", "fx_filter")

  params:add_group("fx_filter_group", "FX Filter", #params_def)

  for _, p in ipairs(params_def) do
    local param_id = "fx_filter_" .. p.id

    if p.type == "option" then
      params:add_option(param_id, p.name, p.options, p.default)
      params:set_action(param_id, function(val)
        -- SC expects 0-indexed options
        node:set(p.id, val - 1)
      end)
    else
      params:add_control(param_id, p.name, p.spec)
      params:set_action(param_id, function(val)
        node:set(p.id, val)
      end)
    end
  end
end

return mod