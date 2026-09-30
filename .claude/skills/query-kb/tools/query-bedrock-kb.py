#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.11"
# dependencies = ["boto3"]
# ///
"""Query an AWS Bedrock Knowledge Base.

Used by the /query-kb skill and the /do-work implementation and review flows.
Normal queries print retrieval results as JSON.

Use --check to test retrieval availability with a built-in neutral query.
Normal mode requires query text.
Use --debug to also write results to bedrock-kb-query-log.md beside this script.
Select a KB with --id or --tag; otherwise the configured default tag is used.

Run this script with --help for syntax and available flags.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys
from typing import Any


DEFAULT_KB_TAG_KEY = "nonchunk-kb"
DEFAULT_KB_TAG_VALUE = "true"
DEFAULT_CHECK_QUERY = "retrieval availability check"
REGION = os.environ.get("AWS_REGION", "us-east-1")
LOG_PATH = Path(__file__).with_name("bedrock-kb-query-log.md")
SELECTOR_FLAGS = {"--kb-id", "--id", "--kb-tag", "--tag"}


def parse_tag(value: str) -> tuple[str, str]:
    if "=" not in value:
        raise argparse.ArgumentTypeError("expected KEY=VALUE")

    key, tag_value = value.split("=", 1)
    key = key.strip()
    tag_value = tag_value.strip()
    if not key or not tag_value:
        raise argparse.ArgumentTypeError("expected KEY=VALUE")

    return key, tag_value


def split_selector(argv: list[str]) -> tuple[list[str], str | None, tuple[str, str] | None]:
    selector_indexes = [
        index
        for index, arg in enumerate(argv)
        if arg in SELECTOR_FLAGS
    ]

    if not selector_indexes:
        return argv, None, None

    if len(selector_indexes) > 1:
        raise ValueError(
            "choose only one KB selector; use either --id or --tag, not both. "
            "Valid forms: "
            "QUERY --id KB_ID or QUERY --tag KEY=VALUE."
        )

    selector_index = selector_indexes[0]
    selector = argv[selector_index]
    if selector_index == 0:
        raise ValueError(
            f"QUERY or --check must come before {selector}."
        )

    selector_value = " ".join(argv[selector_index + 1 :]).strip()
    if not selector_value:
        raise ValueError(f"{selector} requires a value")

    query_args = argv[:selector_index]
    if selector in {"--kb-id", "--id"}:
        return query_args, selector_value, None

    try:
        return query_args, None, parse_tag(selector_value)
    except argparse.ArgumentTypeError as error:
        raise ValueError(f"{selector} requires KEY=VALUE; got {selector_value!r}") from error


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Query an AWS Bedrock Knowledge Base.",
        usage=(
            "%(prog)s [--debug] QUERY [--id KB_ID | --tag KEY=VALUE]\n"
            "       %(prog)s --check [--id KB_ID | --tag KEY=VALUE]"
        ),
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="check retrieval availability with a built-in query",
    )
    parser.add_argument("--debug", action="store_true", help="write full results to the query log")
    selector_help = parser.add_argument_group("kb selection")
    selector_help.add_argument("--id", "--kb-id", dest="kb_id_help", metavar="KB_ID", help="knowledge base id to query; must be last")
    selector_help.add_argument("--tag", "--kb-tag", dest="tag_help", metavar="KEY=VALUE", help="knowledge base tag to resolve; must be last")
    parser.add_argument("query", nargs="*", metavar="QUERY", help="query text for normal retrieval")

    try:
        query_argv, kb_id, tag = split_selector(argv)
    except (argparse.ArgumentTypeError, ValueError) as error:
        parser.error(str(error))

    args = parser.parse_args(query_argv)
    args.kb_id = kb_id
    args.tag = tag
    del args.kb_id_help
    del args.tag_help
    args.query = " ".join(args.query).strip()
    if args.check and args.debug:
        parser.error("--debug cannot be used with --check")
    if not args.query:
        if args.check:
            args.query = DEFAULT_CHECK_QUERY
        else:
            parser.error("query is required unless --check is used")
    return args


def markdown_inline(value: str) -> str:
    return value.replace("`", "\\`").replace("\n", " ").strip()


def code_block(value: str) -> str:
    longest_backticks = 0
    current = 0
    for char in value:
        if char == "`":
            current += 1
            longest_backticks = max(longest_backticks, current)
        else:
            current = 0
    fence = "`" * max(3, longest_backticks + 1)
    return f"{fence}text\n{value.rstrip()}\n{fence}\n"


def ensure_log_header() -> None:
    if not LOG_PATH.exists() or LOG_PATH.stat().st_size == 0:
        LOG_PATH.write_text(
            "# Bedrock KB Query Log\n\n"
            "Each entry stores the query, source metadata, and full retrieved content.\n",
            encoding="utf-8",
        )


def load_boto3() -> Any:
    try:
        import boto3
    except ImportError:
        print("Missing dependency: boto3. Install with `python3 -m pip install boto3`.", file=sys.stderr)
        raise SystemExit(2)
    return boto3


def get_account_id(sts_client: Any) -> str:
    return sts_client.get_caller_identity()["Account"]


def list_all_knowledge_bases(agent_client: Any) -> list[dict[str, Any]]:
    knowledge_bases: list[dict[str, Any]] = []
    paginator = agent_client.get_paginator("list_knowledge_bases")
    for page in paginator.paginate():
        knowledge_bases.extend(page.get("knowledgeBaseSummaries", []))
    return knowledge_bases


def normalize_tags(tags: Any) -> dict[str, str]:
    if isinstance(tags, dict):
        return {str(key): str(value) for key, value in tags.items()}

    normalized: dict[str, str] = {}
    for tag in tags or []:
        key = tag.get("key") or tag.get("Key")
        value = tag.get("value") or tag.get("Value")
        if key is not None and value is not None:
            normalized[str(key)] = str(value)
    return normalized


def resolve_kb_id_by_tag(agent_client: Any, account_id: str, tag_key: str, tag_value: str) -> str | None:
    matches: list[tuple[str, str]] = []
    for kb in list_all_knowledge_bases(agent_client):
        kb_id = kb["knowledgeBaseId"]
        kb_name = kb.get("name", "")
        arn = f"arn:aws:bedrock:{REGION}:{account_id}:knowledge-base/{kb_id}"
        tags = normalize_tags(agent_client.list_tags_for_resource(resourceArn=arn).get("tags"))
        if tags.get(tag_key) == tag_value:
            matches.append((kb_id, kb_name))

    if not matches:
        return None

    if len(matches) > 1:
        formatted = ", ".join(f"{name or '(unnamed)'}={kb_id}" for kb_id, name in matches)
        raise RuntimeError(f"Multiple knowledge bases match {tag_key}={tag_value}: {formatted}")

    return matches[0][0]


def resolve_kb_id(boto3: Any, explicit_kb_id: str | None, explicit_tag: tuple[str, str] | None) -> tuple[str, str]:
    if explicit_kb_id:
        return explicit_kb_id, "id"

    if explicit_tag:
        tag_key, tag_value = explicit_tag
    else:
        tag_key = os.environ.get("BEDROCK_KB_TAG_KEY", DEFAULT_KB_TAG_KEY)
        tag_value = os.environ.get("BEDROCK_KB_TAG_VALUE", DEFAULT_KB_TAG_VALUE)

    agent_client = boto3.client("bedrock-agent", region_name=REGION)
    sts_client = boto3.client("sts", region_name=REGION)
    account_id = get_account_id(sts_client)
    tagged_id = resolve_kb_id_by_tag(agent_client, account_id, tag_key, tag_value)
    if tagged_id:
        return tagged_id, f"tag:{tag_key}={tag_value}"

    raise RuntimeError(f"No knowledge base matched tag {tag_key}={tag_value}")


def retrieve(runtime_client: Any, kb_id: str, query: str) -> dict[str, Any]:
    return runtime_client.retrieve(
        knowledgeBaseId=kb_id,
        retrievalQuery={"text": query},
    )


def retrieve_check(runtime_client: Any, kb_id: str, query: str) -> dict[str, Any]:
    return runtime_client.retrieve(
        knowledgeBaseId=kb_id,
        retrievalQuery={"text": query},
        retrievalConfiguration={
            "managedSearchConfiguration": {
                "numberOfResults": 1,
            }
        },
    )


def append_result_content(log: Any, payload: dict[str, Any]) -> None:
    results = payload.get("retrievalResults", [])
    if not results:
        log.write("\n### Results\n\nNo retrieval results.\n")
        return

    log.write("\n### Results\n")
    for index, item in enumerate(results, start=1):
        metadata = item.get("metadata", {})
        title = metadata.get("_document_title") or item.get("documentId") or "(untitled)"
        source = metadata.get("_source_uri") or item.get("documentId") or "(unknown)"
        score = item.get("score")
        score_text = f"{score:.3f}" if isinstance(score, (int, float)) else "n/a"
        content = item.get("content", {}).get("text", "")

        log.write(f"\n#### {index}. {title}\n\n")
        log.write(f"- Score: `{score_text}`\n")
        log.write(f"- Source: `{markdown_inline(source)}`\n\n")
        log.write(code_block(content or "No text content"))


def append_log(
    timestamp: str,
    query: str,
    kb_id: str,
    kb_resolution: str,
    return_code: int,
    payload: dict[str, Any] | None = None,
    error: str | None = None,
) -> None:
    ensure_log_header()
    with LOG_PATH.open("a", encoding="utf-8") as log:
        log.write("\n---\n\n")
        log.write(f"## {timestamp}\n\n")
        log.write(f"- Query: `{markdown_inline(query)}`\n")
        log.write(f"- Knowledge base: `{kb_id}`\n")
        log.write(f"- KB resolution: `{markdown_inline(kb_resolution)}`\n")
        log.write(f"- Region: `{REGION}`\n")
        log.write(f"- Return code: `{return_code}`\n")

        if return_code == 0 and payload is not None:
            append_result_content(log, payload)
            return

        log.write("\n### Error\n\n")
        log.write(code_block(error or "Unknown error"))


def main() -> None:
    args = parse_args(sys.argv[1:])

    timestamp = datetime.now(timezone.utc).isoformat()
    boto3 = load_boto3()
    kb_id = "(unresolved)"
    kb_resolution = "unresolved"

    try:
        kb_id, kb_resolution = resolve_kb_id(boto3, args.kb_id, args.tag)
        runtime_client = boto3.client("bedrock-agent-runtime", region_name=REGION)
        if args.check:
            retrieve_check(runtime_client, kb_id, args.query)
            print(f"KB retrieval available ({kb_resolution})")
            return
        payload = retrieve(runtime_client, kb_id, args.query)
        print(json.dumps(payload, indent=2, default=str))
        if args.debug:
            append_log(timestamp, args.query, kb_id, kb_resolution, 0, payload=payload)
    except Exception as error:
        message = str(error)
        print(message, file=sys.stderr)
        if args.debug:
            append_log(timestamp, args.query, kb_id, kb_resolution, 1, error=message)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
