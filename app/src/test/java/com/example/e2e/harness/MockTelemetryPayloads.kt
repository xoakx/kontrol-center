package com.example.e2e.harness

/**
 * Deterministic JSON fixtures matching host agent_webui/server.py and telemetry.py.
 * Provides authentic payloads for CPU (20-core Intel Ultra 7 265K), Dual RTX 5060 Ti GPUs,
 * Intel AI Boost NPU, Smart Home sensors, 12 Fleet daemons, and Autonomous RFCs.
 */
object MockTelemetryPayloads {

    fun standardDualGpuTelemetryJson(): String = """
    {
      "cpu": {
        "load_1m": "0.45",
        "load_5m": "0.52",
        "load_15m": "0.48",
        "governor": "performance",
        "temp_c": 42.0,
        "cores": 20
      },
      "memory": {
        "total_mb": 65536,
        "used_mb": 18432,
        "used_pct": 28.1
      },
      "storage": {
        "total_gb": 931.2,
        "used_gb": 361.9,
        "free_gb": 565.3,
        "used_pct": 38.9,
        "mount": "/"
      },
      "npu": {
        "present": true,
        "model": "Intel AI Boost NPU 4 (Arrow Lake)",
        "device": "/dev/accel/accel0",
        "service": "gemini-npu-embeddings.service"
      },
      "gpus": [
        {
          "index": "0",
          "name": "NVIDIA GeForce RTX 5060 Ti",
          "temp_c": 41,
          "util_pct": 8,
          "mem_used_mb": 6442,
          "mem_total_mb": 16311,
          "power_w": 14.2,
          "fan_speed_pct": 0,
          "clock_mhz": 2100
        },
        {
          "index": "1",
          "name": "NVIDIA GeForce RTX 5060 Ti",
          "temp_c": 27,
          "util_pct": 0,
          "mem_used_mb": 10073,
          "mem_total_mb": 16311,
          "power_w": 4.1,
          "fan_speed_pct": 0,
          "clock_mhz": 2100
        }
      ],
      "network": {
        "congestion_control": "bbr",
        "qdisc": "fq"
      },
      "uptime_hours": 142.5
    }
    """.trimIndent()

    fun thermalSpikeTelemetryJson(): String = """
    {
      "cpu": {
        "load_1m": "18.20",
        "load_5m": "12.40",
        "load_15m": "8.10",
        "governor": "performance",
        "temp_c": 86.0,
        "cores": 20
      },
      "memory": {
        "total_mb": 65536,
        "used_mb": 58200,
        "used_pct": 88.8
      },
      "storage": {
        "total_gb": 931.2,
        "used_gb": 361.9,
        "free_gb": 565.3,
        "used_pct": 38.9,
        "mount": "/"
      },
      "npu": {
        "present": true,
        "model": "Intel AI Boost NPU 4 (Arrow Lake)",
        "device": "/dev/accel/accel0",
        "service": "gemini-npu-embeddings.service"
      },
      "gpus": [
        {
          "index": "0",
          "name": "NVIDIA GeForce RTX 5060 Ti",
          "temp_c": 89,
          "util_pct": 98,
          "mem_used_mb": 15800,
          "mem_total_mb": 16311,
          "power_w": 168.5,
          "fan_speed_pct": 92,
          "clock_mhz": 2580
        },
        {
          "index": "1",
          "name": "NVIDIA GeForce RTX 5060 Ti",
          "temp_c": 68,
          "util_pct": 45,
          "mem_used_mb": 11200,
          "mem_total_mb": 16311,
          "power_w": 75.0,
          "fan_speed_pct": 48,
          "clock_mhz": 2200
        }
      ],
      "network": {
        "congestion_control": "bbr",
        "qdisc": "fq"
      },
      "uptime_hours": 142.6
    }
    """.trimIndent()

    fun all12FleetDaemonsJson(): String = """
    {
      "agents": [
        {
          "id": "gemini-scribe",
          "name": "Gemini Scribe Daemon",
          "service": "gemini-scribe.service",
          "category": "Documentation & Telemetry",
          "description": "Monitors sessions and synthesizes clean daily logs.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10101,
            "memory_mb": 85.4
          },
          "is_dynamic": false
        },
        {
          "id": "gemini-sre-watchdog",
          "name": "SRE Watchdog Daemon",
          "service": "gemini-sre-watchdog.service",
          "category": "SRE & Self-Healing",
          "description": "Autonomous system health audit and proactive healing.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10102,
            "memory_mb": 64.2
          },
          "is_dynamic": false
        },
        {
          "id": "gemini-git-custodian",
          "name": "Git Custodian Daemon",
          "service": "gemini-git-custodian.service",
          "category": "Repository Integrity",
          "description": "Monitors git trees, prevents corruption, and manages sync.",
          "status": {
            "active": false,
            "state": "stopped",
            "pid": null,
            "memory_mb": null
          },
          "is_dynamic": false
        },
        {
          "id": "gemini-npu-embeddings",
          "name": "OpenVINO NPU Engine",
          "service": "gemini-npu-embeddings.service",
          "category": "AI Silicon Embedding",
          "description": "Fast local text embedding engine on Intel NPU accel0.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10104,
            "memory_mb": 320.0
          },
          "is_dynamic": false
        },
        {
          "id": "arbitrator",
          "name": "Audio Arbitrator Daemon",
          "service": "arbitrator.service",
          "category": "Audio Subsystem",
          "description": "PipeWire stream routing and priority arbiter.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10105,
            "memory_mb": 42.1
          },
          "is_dynamic": false
        },
        {
          "id": "transcriber",
          "name": "Voice Transcriber Daemon",
          "service": "transcriber.service",
          "category": "Audio Transcriber",
          "description": "Real-time whisper streaming transcription.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10106,
            "memory_mb": 1280.5
          },
          "is_dynamic": false
        },
        {
          "id": "audio-webui",
          "name": "Audio WebUI Service",
          "service": "audio-webui.service",
          "category": "Audio Control WebUI",
          "description": "Web control dashboard for audio mixing and routing.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10107,
            "memory_mb": 95.0
          },
          "is_dynamic": false
        },
        {
          "id": "qwen14b-inference",
          "name": "Qwen 14B Local LLM",
          "service": "qwen14b-inference.service",
          "category": "Local LLM Inference",
          "description": "High-throughput GPU inference engine on RTX 5060 Ti.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10108,
            "memory_mb": 9240.5
          },
          "is_dynamic": false
        },
        {
          "id": "qwen1_5b-reflex",
          "name": "Qwen 1.5B Reflex Model",
          "service": "qwen1_5b-reflex.service",
          "category": "Local Reflex Agent",
          "description": "Low-latency reflex classification and routing daemon.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10109,
            "memory_mb": 2150.0
          },
          "is_dynamic": false
        },
        {
          "id": "auth-monitor",
          "name": "Authentication Monitor",
          "service": "auth-monitor.service",
          "category": "Security & Auth Audit",
          "description": "Suricata and SSH brute-force log parser.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10110,
            "memory_mb": 55.8
          },
          "is_dynamic": false
        },
        {
          "id": "gemini-openobserve",
          "name": "OpenObserve Telemetry",
          "service": "gemini-openobserve.service",
          "category": "Telemetry Ingestion",
          "description": "Vector log and metric pipeline ingestion collector.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10111,
            "memory_mb": 450.2
          },
          "is_dynamic": false
        },
        {
          "id": "gemini-vector",
          "name": "LanceDB Vector Store",
          "service": "gemini-vector.service",
          "category": "Vector DB / LanceDB",
          "description": "Local embedding similarity search daemon.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10112,
            "memory_mb": 512.0
          },
          "is_dynamic": false
        }
      ]
    }
    """.trimIndent()

    fun degradedFleetDaemonsJson(): String = """
    {
      "agents": [
        {
          "id": "gemini-scribe",
          "name": "Gemini Scribe Daemon",
          "service": "gemini-scribe.service",
          "category": "Documentation & Telemetry",
          "description": "Monitors sessions and synthesizes clean daily logs.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10101,
            "memory_mb": 85.4
          },
          "is_dynamic": false
        },
        {
          "id": "gemini-sre-watchdog",
          "name": "SRE Watchdog Daemon",
          "service": "gemini-sre-watchdog.service",
          "category": "SRE & Self-Healing",
          "description": "Autonomous system health audit and proactive healing.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10102,
            "memory_mb": 64.2
          },
          "is_dynamic": false
        },
        {
          "id": "gemini-git-custodian",
          "name": "Git Custodian Daemon",
          "service": "gemini-git-custodian.service",
          "category": "Repository Integrity",
          "description": "Monitors git trees, prevents corruption, and manages sync.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10103,
            "memory_mb": 52.0
          },
          "is_dynamic": false
        },
        {
          "id": "gemini-npu-embeddings",
          "name": "OpenVINO NPU Engine",
          "service": "gemini-npu-embeddings.service",
          "category": "AI Silicon Embedding",
          "description": "Fast local text embedding engine on Intel NPU accel0.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10104,
            "memory_mb": 320.0
          },
          "is_dynamic": false
        },
        {
          "id": "arbitrator",
          "name": "Audio Arbitrator Daemon",
          "service": "arbitrator.service",
          "category": "Audio Subsystem",
          "description": "PipeWire stream routing and priority arbiter.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10105,
            "memory_mb": 42.1
          },
          "is_dynamic": false
        },
        {
          "id": "transcriber",
          "name": "Voice Transcriber Daemon",
          "service": "transcriber.service",
          "category": "Audio Transcriber",
          "description": "Real-time whisper streaming transcription.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10106,
            "memory_mb": 1280.5
          },
          "is_dynamic": false
        },
        {
          "id": "audio-webui",
          "name": "Audio WebUI Service",
          "service": "audio-webui.service",
          "category": "Audio Control WebUI",
          "description": "Web control dashboard for audio mixing and routing.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10107,
            "memory_mb": 95.0
          },
          "is_dynamic": false
        },
        {
          "id": "qwen14b-inference",
          "name": "Qwen 14B Local LLM",
          "service": "qwen14b-inference.service",
          "category": "Local LLM Inference",
          "description": "High-throughput GPU inference engine on RTX 5060 Ti.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10108,
            "memory_mb": 9240.5
          },
          "is_dynamic": false
        },
        {
          "id": "qwen1_5b-reflex",
          "name": "Qwen 1.5B Reflex Model",
          "service": "qwen1_5b-reflex.service",
          "category": "Local Reflex Agent",
          "description": "Low-latency reflex classification and routing daemon.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10109,
            "memory_mb": 2150.0
          },
          "is_dynamic": false
        },
        {
          "id": "auth-monitor",
          "name": "Authentication Monitor",
          "service": "auth-monitor.service",
          "category": "Security & Auth Audit",
          "description": "Suricata and SSH brute-force log parser.",
          "status": {
            "active": false,
            "state": "failed",
            "pid": null,
            "memory_mb": null
          },
          "is_dynamic": false
        },
        {
          "id": "gemini-openobserve",
          "name": "OpenObserve Telemetry",
          "service": "gemini-openobserve.service",
          "category": "Telemetry Ingestion",
          "description": "Vector log and metric pipeline ingestion collector.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10111,
            "memory_mb": 450.2
          },
          "is_dynamic": false
        },
        {
          "id": "gemini-vector",
          "name": "LanceDB Vector Store",
          "service": "gemini-vector.service",
          "category": "Vector DB / LanceDB",
          "description": "Local embedding similarity search daemon.",
          "status": {
            "active": true,
            "state": "running",
            "pid": 10112,
            "memory_mb": 512.0
          },
          "is_dynamic": false
        }
      ]
    }
    """.trimIndent()

    fun standardSmartHomeJson(): String = """
    {
      "sensors": {
        "apollo_msr2": {
          "name": "Apollo MSR-2 Multisensor",
          "presence": true,
          "target_distance_m": 1.45,
          "movement_energy": 35,
          "still_energy": 42,
          "illuminance_lux": 120.5,
          "co2_ppm": 640
        }
      },
      "voice_satellites": {
        "xvf3800": {
          "name": "XMOS XVF3800 Voice Satellite",
          "state": "listening",
          "beam_angle_deg": 185,
          "wake_word": "Okay Nabu",
          "location": "Living Room",
          "last_intent": "Voice assistant wake ping verified"
        }
      },
      "zigbee_perimeter": {
        "sonoff_door": {
          "name": "SNZB-04 Door Sensor",
          "state": "closed",
          "battery_pct": 95,
          "lqi": 140,
          "last_changed": "10 minutes ago"
        }
      },
      "air_purifier": {
        "levoit_purifier": {
          "name": "Levoit Core 400S Air Purifier",
          "power": "on",
          "mode": "auto",
          "fan_speed": 2,
          "pm25_aqi": 4,
          "air_quality": "Excellent",
          "filter_life_pct": 88
        }
      }
    }
    """.trimIndent()

    fun pendingRfcsJson(): String = """
    {
      "rfcs": [
        {
          "id": "RFC-00142",
          "source": "SRE Autonomous Watchdog",
          "title": "Perf Tune: CPU Frequency Scaling Governor Alignment",
          "category": "OPTIMIZATION",
          "description": "Lock CPU governor to throughput-performance and pin audio affinity",
          "proposed_steps": [
            "tuned-adm profile throughput-performance",
            "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > ${'$'}g; done"
          ],
          "risk_level": "MEDIUM",
          "status": "PROPOSED",
          "created_at": "2026-09-20T00:01:00Z",
          "updated_at": "2026-09-20T00:01:00Z"
        },
        {
          "id": "RFC-00143",
          "source": "Git Custodian",
          "title": "Purge Stale Chromium and Shader Caches",
          "category": "MAINTENANCE",
          "description": "Clean up unused temporary caches to recover NVMe block storage",
          "proposed_steps": [
            "rm -rf ~/.cache/google-chrome/Default/Cache/*"
          ],
          "risk_level": "LOW",
          "status": "PROPOSED",
          "created_at": "2026-09-20T00:02:00Z",
          "updated_at": "2026-09-20T00:02:00Z"
        },
        {
          "id": "RFC-00144",
          "source": "NetSec Bouncer",
          "title": "Blacklist Persistent Brute-Force Subnet via nftables",
          "category": "SECURITY",
          "description": "Add malicious subnet to crowdsec drop chain",
          "proposed_steps": [
            "nft add element inet filter crowdsec-blacklists { 203.0.113.0/24 }"
          ],
          "risk_level": "HIGH",
          "status": "PROPOSED",
          "created_at": "2026-09-20T00:03:00Z",
          "updated_at": "2026-09-20T00:03:00Z"
        }
      ]
    }
    """.trimIndent()
}
