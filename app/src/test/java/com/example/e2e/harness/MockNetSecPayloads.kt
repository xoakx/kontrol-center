package com.example.e2e.harness

/**
 * Deterministic JSON fixtures matching host NetSec stack:
 * Suricata 8 eve.json alerts, CrowdSec decisions, Tetragon eBPF logs, and nftables firewall posture.
 * Matches both NetSecDto (Retrofit responses) and raw daemon outputs.
 */
object MockNetSecPayloads {

    fun netsecOverviewJson(): String = """
    {
      "status": "healthy",
      "suricata_active": true,
      "suricata_alert_count": 5,
      "crowdsec_active": true,
      "crowdsec_ban_count": 2,
      "tetragon_health": "healthy",
      "total_dropped_packets": 14820
    }
    """.trimIndent()

    fun suricataAlertsJson(): String = """
    {
      "status": "success",
      "count": 3,
      "alerts": [
        {
          "timestamp": "2026-09-20T00:10:00.123456Z",
          "flow_id": 12345678,
          "event_type": "alert",
          "src_ip": "203.0.113.195",
          "src_port": 54321,
          "dest_ip": "192.168.1.161",
          "dest_port": 22,
          "proto": "TCP",
          "alert": {
            "action": "allowed",
            "gid": 1,
            "signature_id": 2001219,
            "rev": 20,
            "signature": "ET EXPLOIT Remote Command Execution Attempt",
            "category": "Attempted Administrator Privilege Gain",
            "severity": 1
          }
        },
        {
          "timestamp": "2026-09-20T00:10:05.123456Z",
          "flow_id": 12345679,
          "event_type": "alert",
          "src_ip": "198.51.100.42",
          "src_port": 43210,
          "dest_ip": "192.168.1.161",
          "dest_port": 80,
          "proto": "TCP",
          "alert": {
            "action": "allowed",
            "gid": 1,
            "signature_id": 2001220,
            "rev": 5,
            "signature": "ET SCAN Nmap SYN Scan",
            "category": "Attempted Information Leak",
            "severity": 2
          }
        },
        {
          "timestamp": "2026-09-20T00:10:10.123456Z",
          "flow_id": 12345680,
          "event_type": "alert",
          "src_ip": "192.168.1.1",
          "src_port": 53,
          "dest_ip": "192.168.1.161",
          "dest_port": 45678,
          "proto": "UDP",
          "alert": {
            "action": "allowed",
            "gid": 1,
            "signature_id": 2001221,
            "rev": 2,
            "signature": "ET INFO DNS Query for Known Domain",
            "category": "Generic Protocol Command Decode",
            "severity": 3
          }
        }
      ]
    }
    """.trimIndent()

    fun crowdsecDecisionsJson(): String = """
    {
      "status": "success",
      "action": "list",
      "decision_count": 2,
      "active_decisions": [
        {
          "id": 14951,
          "origin": "CAPI",
          "type": "ban",
          "scope": "Ip",
          "value": "209.99.190.113",
          "duration": "3h 45m",
          "until": "2026-09-20T03:55:00Z",
          "scenario": "ssh:bruteforce"
        },
        {
          "id": 14952,
          "origin": "cscli",
          "type": "ban",
          "scope": "Ip",
          "value": "198.51.100.4",
          "duration": "11h 20m",
          "until": "2026-09-20T11:30:00Z",
          "scenario": "http:crawl"
        }
      ],
      "bouncers": [
        {
          "name": "FirewallBouncer",
          "type": "nftables",
          "valid": true
        },
        {
          "name": "cloudflare-bouncer",
          "type": "api",
          "valid": true
        }
      ]
    }
    """.trimIndent()

    fun tetragonStatusJson(): String = """
    {
      "status": "healthy",
      "tracing_policies": [
        {
          "name": "anti-reverse-shell",
          "mode": "enforce"
        },
        {
          "name": "scratch-exec",
          "mode": "enforce"
        },
        {
          "name": "namespace-audit",
          "mode": "monitor_only"
        },
        {
          "name": "sudo-monitor",
          "mode": "monitor_only"
        },
        {
          "name": "file-integrity",
          "mode": "monitor_only"
        }
      ],
      "recent_events": [
        {
          "process_kprobe": {
            "binary": "/bin/bash",
            "pid": 1639879,
            "function_name": "tcp_connect",
            "action": "KPROBE_ACTION_SIGKILL"
          }
        }
      ]
    }
    """.trimIndent()

    fun firewallStatusJson(): String = """
    {
      "status": "active",
      "tableCount": 11,
      "totalDroppedPackets": 14820,
      "dropRules": [
        {
          "chain": "ts-input",
          "packets": 420
        },
        {
          "chain": "lan-input",
          "packets": 14400
        }
      ]
    }
    """.trimIndent()

    fun bouncersStatusJson(): String = """
    [
      {
        "name": "FirewallBouncer",
        "type": "nftables",
        "valid": true
      },
      {
        "name": "cloudflare-bouncer",
        "type": "api",
        "valid": true
      }
    ]
    """.trimIndent()
}
