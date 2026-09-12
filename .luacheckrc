-- norns runtime globals for lib/mod.lua
std = "lua53"
max_line_length = false
read_globals = {
  "params", "controlspec", "osc", "clock", "_menu",
  "screen", "util", "metro", "norns", "engine",
}
globals = { "init", "cleanup", "redraw", "key", "enc" }
self = false  -- colon-style methods that do not touch self are fine
