-- fx_filter: DFM1 filter plugin for sixolet's fx mod framework.

local fx = require("fx/lib/fx")
local mod = require 'core/mods'

local FxFilter = fx:new{
  subpath = "/fx_filter"
}

-- Clock divisions for synced LFO. Beats assume 4/4 (1 bar = 4 beats).
local LFO_DIVS = {
  { name = "4 bars", beats = 16 },
  { name = "2 bars", beats = 8 },
  { name = "1 bar",  beats = 4 },
  { name = "1/2.",   beats = 3 },
  { name = "1/2",    beats = 2 },
  { name = "1/4.",   beats = 1.5 },
  { name = "1/4",    beats = 1 },
  { name = "1/4t",   beats = 2 / 3 },
  { name = "1/8.",   beats = 0.75 },
  { name = "1/8",    beats = 0.5 },
  { name = "1/8t",   beats = 1 / 3 },
  { name = "1/16",   beats = 0.25 },
  { name = "1/32",   beats = 0.125 },
}

local lfo_div_names = {}
for i, d in ipairs(LFO_DIVS) do lfo_div_names[i] = d.name end

local sync_clock = nil

function FxFilter:send(key, val)
  osc.send({ "localhost", 57120 }, self.subpath .. "/set", { key, val })
end

function FxFilter:lfo_synced()
  return params:get("fx_filter_lfo_mode") == 2
end

function FxFilter:stop_lfo_sync()
  if sync_clock then
    clock.cancel(sync_clock)
    sync_clock = nil
  end
end

-- Runs while lfo mode is "sync". Each cycle: wait for the next division
-- boundary, push the tempo-derived rate, and restart the LFO phase so it
-- stays locked to the beat grid through tempo changes and transport jumps.
function FxFilter:start_lfo_sync()
  self:stop_lfo_sync()
  local beats = LFO_DIVS[params:get("fx_filter_lfo_div")].beats
  local last_rate = nil
  local function push_rate()
    local rate = clock.get_tempo() / 60 / beats
    if rate ~= last_rate then
      self:send("lfo_rate", rate)
      last_rate = rate
    end
  end
  push_rate()
  sync_clock = clock.run(function()
    while true do
      clock.sync(beats)
      push_rate()
      self:send("lfo_reset", 1)
    end
  end)
end

function FxFilter:update_lfo_mode()
  if self:lfo_synced() then
    params:hide("fx_filter_lfo_rate")
    params:show("fx_filter_lfo_div")
    self:start_lfo_sync()
  else
    params:show("fx_filter_lfo_rate")
    params:hide("fx_filter_lfo_div")
    self:stop_lfo_sync()
    -- Restore the free-running rate the sync loop overwrote.
    params:lookup_param("fx_filter_lfo_rate"):bang()
  end
  _menu.rebuild_params()
end

function FxFilter:add_params()
  params:add_separator("fx_filter", "fx filter")
  self:add_slot("fx_filter_slot", "slot")

  params:add_group("fx_filter_grp_filter", "filter", 6)
  self:add_control("fx_filter_cutoff", "cutoff", "cutoff",
    controlspec.new(20, 20000, 'exp', 0, 1000, "Hz"))
  self:add_control("fx_filter_res", "resonance", "res",
    controlspec.new(0, 1.2, 'lin', 0.01, 0.1, ""))
  self:add_option("fx_filter_type", "type", "type", {"lowpass", "highpass"}, 1)
  self:add_taper("fx_filter_dfm_gain", "input gain", "dfm_gain", 0.1, 8, 1, 1, "")
  self:add_control("fx_filter_stereo_spread", "stereo spread", "stereo_spread",
    controlspec.new(0, 1, 'lin', 0.01, 0, ""))
  self:add_control("fx_filter_noise", "noise", "noise",
    controlspec.new(0, 0.005, 'lin', 0.0001, 0.0003, ""))

  params:add_group("fx_filter_grp_drive", "drive", 2)
  self:add_option("fx_filter_drive_mode", "drive mode", "drive_mode",
    {"off", "pre-filter", "post-filter"}, 1)
  self:add_taper("fx_filter_drive_amount", "drive amount", "drive_amount", 1, 8, 1, 1, "")

  params:add_group("fx_filter_grp_env", "envelope", 4)
  self:add_control("fx_filter_env_amount", "env > cutoff", "env_amount",
    controlspec.new(-4, 4, 'lin', 0.05, 0, "oct"))
  self:add_taper("fx_filter_env_sens", "env sensitivity", "env_sens", 0, 20, 2, 1, "")
  self:add_taper("fx_filter_env_attack", "env attack", "env_attack", 0.001, 1, 0.01, 4, "s")
  self:add_taper("fx_filter_env_release", "env release", "env_release", 0.01, 2, 0.2, 3, "s")

  params:add_group("fx_filter_grp_lfo", "lfo", 5)
  self:add_option("fx_filter_lfo_shape", "lfo shape", "lfo_shape",
    {"sine", "triangle", "saw", "square", "s&h", "noise"}, 1)
  params:add_option("fx_filter_lfo_mode", "lfo mode", {"free", "sync"}, 1)
  params:set_action("fx_filter_lfo_mode", function() self:update_lfo_mode() end)
  self:add_taper("fx_filter_lfo_rate", "lfo rate", "lfo_rate", 0.01, 20, 1, 3, "Hz")
  params:add_option("fx_filter_lfo_div", "lfo division", lfo_div_names, 7)
  params:set_action("fx_filter_lfo_div", function()
    if self:lfo_synced() then self:start_lfo_sync() end
  end)
  params:hide("fx_filter_lfo_div")
  -- UI shows -127..127, SC side takes -4..4 octaves.
  params:add_control("fx_filter_lfo_depth", "lfo > cutoff",
    controlspec.new(-127, 127, 'lin', 1, 0, ""))
  params:set_action("fx_filter_lfo_depth", function(val)
    self:send("lfo_depth", val / 127 * 4)
  end)

  params:add_group("fx_filter_grp_out", "output", 2)
  self:add_control("fx_filter_out_gain", "level", "out_gain",
    controlspec.new(-24, 12, 'lin', 0.5, 0, "dB"))
  self:add_option("fx_filter_limiter", "limiter", "limiter", {"off", "soft clip", "limiter"}, 3)
end

mod.hook.register("script_pre_init", "filter mod pre init", function()
  FxFilter:install()
end)

mod.hook.register("script_post_cleanup", "filter mod post cleanup", function()
  FxFilter:stop_lfo_sync()
end)

return FxFilter
