#!/usr/bin/env python3
"""
AI 拟人聊天 A/B 评测脚本。
对比旧版 DashScope 聊天接口（/helloworld/simple/chat）与新版 DeepSeek 聊天接口。

用法：
    python ai_eval.py --base http://localhost:8080/api --token <jwt> \
        --cases ai-eval/ai_eval_cases.json --out ai-eval/results.json

评分维度（规格 16.1）：
    角色一致性 20% / 上下文理解 15% / 长期记忆准确性 20% / 自然程度 20% / 情绪回应 15% / 不编造记忆 10%
说明：
    本脚本负责采集回复并生成可人工打分的结果表；打分/统计由维护者按评分卡执行。
"""
import argparse
import json
import sys
import time
import urllib.parse
import urllib.request


def post(base, path, body, token, timeout=120):
    req = urllib.request.Request(
        base + path,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
            "Authorization": "Bearer " + token,
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8")


def get(base, path, token, timeout=60):
    req = urllib.request.Request(
        base + path,
        headers={"Authorization": "Bearer " + token},
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def parse_sse(raw):
    content = ""
    message_id = None
    for line in raw.splitlines():
        if not line.startswith("data: "):
            continue
        payload = line[len("data: "):]
        try:
            event = json.loads(payload)
        except json.JSONDecodeError:
            continue
        if event.get("type") == "delta":
            content += event.get("data", {}).get("content", "")
        elif event.get("type") in ("start", "done"):
            message_id = event.get("data", {}).get("messageId", message_id)
    return content, message_id


def run_new(base, token, cases, character_id):
    results = []
    conv = post(base, "/ai/conversations", {"characterId": character_id}, token).strip()
    # 非 SSE，走 JSON
    conv_resp = json.loads(conv)
    conversation_id = conv_resp.get("data", {}).get("id")
    if not conversation_id:
        print("创建会话失败:", conv, file=sys.stderr)
        return results
    for case in cases:
        client_id = f"eval-{case['id']}-{int(time.time()*1000)}"
        for msg in case["messages"]:
            t0 = time.time()
            raw = post(
                base,
                f"/ai/conversations/{conversation_id}/messages",
                {"content": msg, "clientMessageId": client_id + "-" + str(abs(hash(msg)))},
                token,
            )
            reply, mid = parse_sse(raw)
            latency = round((time.time() - t0) * 1000, 1)
            results.append({
                "id": case["id"], "scenario": case["scenario"], "message": msg,
                "reply": reply, "messageId": mid, "latencyMs": latency,
            })
    return results


def run_old(base, token, cases):
    results = []
    for case in cases:
        for msg in case["messages"]:
            req = urllib.request.Request(
                base + "/helloworld/simple/chat?query=" + urllib.parse.quote(msg),
                headers={"Authorization": "Bearer " + token},
                method="GET",
            )
            with urllib.request.urlopen(req, timeout=120) as resp:
                reply = resp.read().decode("utf-8")
            results.append({
                "id": case["id"], "scenario": case["scenario"], "message": msg,
                "reply": reply, "latencyMs": None,
            })
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080/api")
    parser.add_argument("--token", required=True)
    parser.add_argument("--cases", default="ai-eval/ai_eval_cases.json")
    parser.add_argument("--out", default="ai-eval/results.json")
    parser.add_argument("--character-id", type=int, default=1)
    args = parser.parse_args()

    with open(args.cases, encoding="utf-8") as f:
        cases = json.load(f)

    report = {
        "old": run_old(args.base, args.token, cases),
        "new": run_new(args.base, args.token, cases, args.character_id),
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"完成，共采集 {len(report['old'])} 条旧版 / {len(report['new'])} 条新版回复，结果写入 {args.out}")


if __name__ == "__main__":
    main()
