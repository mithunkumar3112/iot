const supabase = require('../utils/supabase');

// In-memory cooldown storage: { deviceId_appName: timestamp }
const alertCooldowns = {};
const COOLDOWN_MS = 30000; // 30 seconds

exports.trackProcesses = async (req, res) => {
  try {
    const { deviceId, processes } = req.body;

    if (!deviceId || !processes || !Array.isArray(processes)) {
      return res.status(400).json({ error: 'Invalid payload' });
    }

    // 1. Log processes to Supabase
    const logs = processes.map(p => ({
      device_id: deviceId,
      process_name: p.name,
      cpu_usage: p.cpu,
      memory_usage: p.memory,
      timestamp: p.timestamp || new Date().toISOString()
    }));

    const { error: logError } = await supabase
      .from('process_logs')
      .insert(logs);

    if (logError) {
      console.error('❌ Error logging processes to Supabase:', logError);
    }

    // 2. Detect target apps (e.g., excel.exe)
    const targetApps = ['excel.exe'];
    const detectedApps = processes.filter(p => targetApps.includes(p.name.toLowerCase()));

    for (const app of detectedApps) {
      const cooldownKey = `${deviceId}_${app.name.toLowerCase()}`;
      const now = Date.now();

      if (!alertCooldowns[cooldownKey] || (now - alertCooldowns[cooldownKey] > COOLDOWN_MS)) {
        // Trigger detection event
        console.log(`🎯 TARGET DETECTED: ${app.name} on ${deviceId}`);
        alertCooldowns[cooldownKey] = now;

        const alertPayload = {
          deviceId,
          app: app.name,
          message: `${app.name} is running`,
          timestamp: new Date().toISOString()
        };

        // Emit WebSocket event
        const io = req.app.get('socketio');
        if (io) {
          io.emit('app_detected', alertPayload);
        }

        // Log detection to database
        const { error: detectionError } = await supabase
          .from('app_detections')
          .insert([{
            device_id: deviceId,
            app_name: app.name,
            message: alertPayload.message,
            timestamp: alertPayload.timestamp
          }]);

        if (detectionError) {
          console.error('❌ Error logging detection to Supabase:', detectionError);
        }
      }
    }

    res.status(200).json({ status: 'OK', processed: processes.length });

  } catch (err) {
    console.error('Process Controller Error:', err);
    res.status(500).json({ error: 'Internal server error' });
  }
};

exports.getHistory = async (req, res) => {
  try {
    const { deviceId, appName, startDate, endDate } = req.query;

    let queryLogs = supabase.from('process_logs').select('*');
    let queryDetections = supabase.from('app_detections').select('*');

    if (deviceId) {
      queryLogs = queryLogs.eq('device_id', deviceId);
      queryDetections = queryDetections.eq('device_id', deviceId);
    }
    if (appName) {
      queryLogs = queryLogs.ilike('process_name', `%${appName}%`);
      queryDetections = queryDetections.ilike('app_name', `%${appName}%`);
    }
    if (startDate) {
      queryLogs = queryLogs.gte('timestamp', startDate);
      queryDetections = queryDetections.gte('timestamp', startDate);
    }
    if (endDate) {
      queryLogs = queryLogs.lte('timestamp', endDate);
      queryDetections = queryDetections.lte('timestamp', endDate);
    }

    const [logsRes, alertsRes] = await Promise.all([
      queryLogs.order('timestamp', { ascending: false }).limit(100),
      queryDetections.order('timestamp', { ascending: false }).limit(50)
    ]);

    if (logsRes.error) throw logsRes.error;
    if (alertsRes.error) throw alertsRes.error;

    // Combine and sort
    const history = [
      ...logsRes.data.map(l => ({ ...l, type: 'process' })),
      ...alertsRes.data.map(a => ({ ...a, type: 'alert' }))
    ].sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));

    res.status(200).json(history);

  } catch (err) {
    console.error('History Retrieval Error:', err);
    res.status(500).json({ error: 'Error fetching history' });
  }
};
