// fx_filter engine for norns fx framework
// Upgraded with parameter smoothing, DC leakage protection,
// dual-stage drive (DFM1 gain + tanh soft-clipping), and stereo spread.

SynthDef(\fx_filter, {
    arg inBus, outBus,
        cutoff = 1000,
        res = 0.1,
        dfm_gain = 1.0,
        type = 0.0,
        noise = 0.0003,
        drive_amount = 1.0,
        drive_mode = 0, // 0 = Off, 1 = Pre-Filter, 2 = Post-Filter
        stereo_spread = 0.0,
        mix = 1.0;

    var inSig, procL, procR, filtSig, finalSig;

    // Parameter smoothing to eliminate zipper noise and clicks
    var f_cutoff = Lag.kr(cutoff.clip(20, 20000), 0.03);
    var r_res    = Lag.kr(res.clip(0.0, 1.2), 0.03);
    var g_dfm    = Lag.kr(dfm_gain.clip(0.1, 10.0), 0.03);
    var d_amt    = Lag.kr(drive_amount.clip(1.0, 10.0), 0.03);
    var s_spread = Lag.kr(stereo_spread.clip(0.0, 1.0), 0.03);
    var m_mix    = Lag.kr(mix.clip(0.0, 1.0), 0.02);

    // Calculate stereo cutoff frequencies (offsetting L/R based on spread)
    var freqL = (f_cutoff * (1 - (s_spread * 0.35))).clip(20, 20000);
    var freqR = (f_cutoff * (1 + (s_spread * 0.35))).clip(20, 20000);

    // Tanh drive output compensation factor
    var driveComp = 1 / (d_amt.sqrt);

    inSig = In.ar(inBus, 2);

    // Pre-Filter Drive Stage
    procL = Select.ar(drive_mode >= 1, [inSig[0], (inSig[0] * d_amt).tanh * driveComp]);
    procR = Select.ar(drive_mode >= 1, [inSig[1], (inSig[1] * d_amt).tanh * driveComp]);

    // DFM1 Filter Processing
    procL = DFM1.ar(procL, freqL, r_res, g_dfm, type, noise);
    procR = DFM1.ar(procR, freqR, r_res, g_dfm, type, noise);

    // Post-Filter Drive Stage (tames high self-oscillation peaks)
    procL = Select.ar(drive_mode >= 2, [procL, (procL * d_amt).tanh * driveComp]);
    procR = Select.ar(drive_mode >= 2, [procR, (procR * d_amt).tanh * driveComp]);

    filtSig = [procL, procR];

    // DC Offset Leakage Protection
    filtSig = LeakDC.ar(filtSig);

    // Smooth Dry/Wet Crossfade
    finalSig = XFade2.ar(inSig, filtSig, m_mix.linlin(0.0, 1.0, -1.0, 1.0));

    ReplaceOut.ar(outBus, finalSig);
}).add;