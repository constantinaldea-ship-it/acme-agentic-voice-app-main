#!/usr/bin/env python3
"""Shared instruction contract parsing and validation helpers.

Author: Codex
Date: 2026-03-27
"""

from __future__ import annotations

from dataclasses import dataclass
from fnmatch import fnmatchcase
import json
from pathlib import Path
import re


SECTION_NAME_PATTERN = r"(role|persona|constraints|taskflow|examples)"
SECTION_OPEN_RE = re.compile(rf"^<(?P<tag>{SECTION_NAME_PATTERN})>", re.IGNORECASE)
SECTION_CLOSE_RE = re.compile(rf"</(?P<tag>{SECTION_NAME_PATTERN})>$", re.IGNORECASE)
AGENT_REF_RE = re.compile(r"\{@AGENT:\s*([^}]+)\}")
TOOL_REF_RE = re.compile(r"\{@TOOL:\s*([^}]+)\}")
EXAMPLE_OPEN_RE = re.compile(r"^<example>$", re.IGNORECASE)
TOOL_CALL_RE = re.compile(r"([a-zA-Z_][a-zA-Z0-9_.]*)\(([^)]*)\)")
EXAMPLE_BLOCK_RE = re.compile(r"<example>\s*(?P<body>.*?)\s*</example>", re.DOTALL | re.IGNORECASE)
EXAMPLE_FIELD_RE = re.compile(r"<(?P<tag>user|tool_call|agent)>\s*(?P<body>.*?)\s*</(?P=tag)>", re.DOTALL | re.IGNORECASE)


def _normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def _normalize_rel_path(value: str) -> str:
    return value.replace("\\", "/").strip("/")


@dataclass(frozen=True)
class SectionInstance:
    """Concrete top-level section discovered in an instruction file."""

    name: str
    start_line: int
    end_line: int


@dataclass(frozen=True)
class ExampleContract:
    """Parsed example block from an instruction file."""

    user: str | None
    tool_calls: tuple[str, ...]
    agent: str | None
    raw_body: str


@dataclass(frozen=True)
class InstructionContract:
    """Parsed instruction file with structural metadata."""

    path: Path
    relative_path: str
    agent_name: str
    raw_text: str
    sections: tuple[SectionInstance, ...]
    agent_refs: tuple[str, ...]
    direct_tool_refs: tuple[str, ...]
    tool_calls: tuple[str, ...]
    examples: tuple[ExampleContract, ...]
    example_count: int
    parse_error: str | None

    @property
    def section_names_in_order(self) -> tuple[str, ...]:
        """Return top-level section names in the order they appear."""
        return tuple(section.name for section in self.sections)

    @property
    def sections_by_name(self) -> dict[str, tuple[SectionInstance, ...]]:
        """Group section instances by section name."""
        grouped: dict[str, list[SectionInstance]] = {}
        for section in self.sections:
            grouped.setdefault(section.name, []).append(section)
        return {name: tuple(values) for name, values in grouped.items()}

    @classmethod
    def load(
        cls,
        path: Path,
        *,
        package_root: Path | None = None,
        agent_name: str | None = None,
    ) -> "InstructionContract":
        """Load and parse a CES instruction file from disk."""
        raw_text = path.read_text(encoding="utf-8")
        lines = raw_text.splitlines()
        open_sections: list[tuple[str, int]] = []
        sections: list[SectionInstance] = []
        agent_refs: list[str] = []
        direct_tool_refs: list[str] = []
        tool_calls: list[str] = []
        example_count = 0

        for index, line in enumerate(lines, start=1):
            trimmed = line.strip()

            open_match = SECTION_OPEN_RE.match(trimmed)
            if open_match:
                open_sections.append((open_match.group("tag").lower(), index))

            close_match = SECTION_CLOSE_RE.search(trimmed)
            if close_match:
                close_name = close_match.group("tag").lower()
                open_index = _find_last_open_section(open_sections, close_name)
                if open_index != -1:
                    open_name, start_line = open_sections.pop(open_index)
                    sections.append(
                        SectionInstance(
                            name=open_name,
                            start_line=start_line,
                            end_line=index,
                        )
                    )

            if EXAMPLE_OPEN_RE.match(trimmed):
                example_count += 1

            for match in AGENT_REF_RE.finditer(line):
                agent_refs.append(match.group(1).strip())

            for match in TOOL_REF_RE.finditer(line):
                direct_tool_refs.append(match.group(1).strip())

            if _is_inside_section(open_sections, "examples"):
                for match in TOOL_CALL_RE.finditer(line):
                    operation = match.group(1).strip()
                    if _is_likely_tool_call(operation):
                        tool_calls.append(operation)

        parse_error = None
        if open_sections:
            parse_error = (
                "Unclosed section(s): "
                + ", ".join(f"<{name}> at line {start_line}" for name, start_line in open_sections)
            )

        examples: list[ExampleContract] = []
        for example_match in EXAMPLE_BLOCK_RE.finditer(raw_text):
            example_body = example_match.group("body")
            user: str | None = None
            agent: str | None = None
            example_tool_calls: list[str] = []
            for field_match in EXAMPLE_FIELD_RE.finditer(example_body):
                tag = field_match.group("tag").lower()
                value = _normalize_text(field_match.group("body"))
                if tag == "user":
                    user = value
                elif tag == "agent":
                    agent = value
                elif tag == "tool_call":
                    example_tool_calls.append(value)
            examples.append(
                ExampleContract(
                    user=user,
                    tool_calls=tuple(example_tool_calls),
                    agent=agent,
                    raw_body=example_body.strip(),
                )
            )

        root = package_root.resolve() if package_root else path.parent.resolve()
        relative_path = _normalize_rel_path(str(path.resolve().relative_to(root)))
        resolved_agent_name = agent_name or (
            "__global__" if path.name == "global_instruction.txt" else path.parent.name
        )

        return cls(
            path=path,
            relative_path=relative_path,
            agent_name=resolved_agent_name,
            raw_text=raw_text,
            sections=tuple(sections),
            agent_refs=tuple(sorted(set(agent_refs))),
            direct_tool_refs=tuple(sorted(set(direct_tool_refs))),
            tool_calls=tuple(sorted(set(tool_calls))),
            examples=tuple(examples),
            example_count=example_count,
            parse_error=parse_error,
        )


@dataclass(frozen=True)
class AgentManifestContract:
    """Tool and operation inventory declared by an agent manifest."""

    path: Path
    display_name: str
    direct_tools: tuple[str, ...]
    toolset_names: tuple[str, ...]
    toolset_operations: tuple[str, ...]

    @classmethod
    def load(cls, path: Path) -> "AgentManifestContract":
        """Load an agent manifest and expose its callable inventory."""
        data = json.loads(path.read_text(encoding="utf-8"))
        toolset_names: list[str] = []
        toolset_operations: list[str] = []
        for toolset_config in data.get("toolsets", []):
            if not isinstance(toolset_config, dict):
                continue
            toolset_name = toolset_config.get("toolset")
            if isinstance(toolset_name, str) and toolset_name:
                toolset_names.append(toolset_name)
            for tool_id in toolset_config.get("toolIds", []):
                if isinstance(tool_id, str) and toolset_name:
                    toolset_operations.append(f"{toolset_name}.{tool_id}")

        return cls(
            path=path,
            display_name=data["displayName"],
            direct_tools=tuple(sorted(t for t in data.get("tools", []) if isinstance(t, str))),
            toolset_names=tuple(sorted(set(toolset_names))),
            toolset_operations=tuple(sorted(set(toolset_operations))),
        )


@dataclass(frozen=True)
class InstructionStructureRule:
    """Declarative rule describing the allowed top-level structure of an instruction."""

    identifier: str
    path_patterns: tuple[str, ...]
    allowed_section_orders: tuple[tuple[str, ...], ...]
    require_non_empty_examples: bool

    def matches(self, relative_path: str) -> bool:
        """Return True when the rule applies to the relative instruction path."""
        normalized_path = _normalize_rel_path(relative_path)
        return any(fnmatchcase(normalized_path, pattern) for pattern in self.path_patterns)


@dataclass(frozen=True)
class InstructionRuleSet:
    """Loaded instruction structure rules."""

    version: int
    contracts: tuple[InstructionStructureRule, ...]

    def find_rule(self, relative_path: str) -> InstructionStructureRule | None:
        """Return the first matching instruction rule for the relative path."""
        normalized_path = _normalize_rel_path(relative_path)
        for contract in self.contracts:
            if contract.matches(normalized_path):
                return contract
        return None


@dataclass(frozen=True)
class InstructionValidationFinding:
    """A contract or reference validation finding."""

    code: str
    severity: str
    message: str
    line: int | None = None


def default_rule_set_path() -> Path:
    """Return the local instruction contract path owned by ces-agent scripts."""
    return Path(__file__).resolve().parent / "contracts" / "instruction-contract-rules.json"


def load_instruction_rule_set(rule_set_path: Path | None = None) -> InstructionRuleSet:
    """Load the shared instruction structure rules."""
    path = rule_set_path or default_rule_set_path()
    data = json.loads(path.read_text(encoding="utf-8"))
    return InstructionRuleSet(
        version=int(data["version"]),
        contracts=tuple(
            InstructionStructureRule(
                identifier=contract["id"],
                path_patterns=tuple(_normalize_rel_path(pattern) for pattern in contract["pathPatterns"]),
                allowed_section_orders=tuple(tuple(order) for order in contract["allowedSectionOrders"]),
                require_non_empty_examples=bool(contract.get("requireNonEmptyExamples", False)),
            )
            for contract in data["contracts"]
        ),
    )


def find_missing_sections(
    contract: InstructionContract,
    structure_rule: InstructionStructureRule,
) -> list[str]:
    """Return missing sections against the closest allowed section order."""
    expected_order = _best_matching_section_order(contract.section_names_in_order, structure_rule.allowed_section_orders)
    actual_names = set(contract.section_names_in_order)
    return [name for name in expected_order if name not in actual_names]


def find_unknown_direct_tools(
    contract: InstructionContract,
    manifest: AgentManifestContract,
) -> list[str]:
    """Return direct tool references used in the prompt but not declared by the agent."""
    known_tools = set(manifest.direct_tools)
    return sorted(tool for tool in contract.direct_tool_refs if tool not in known_tools)


def find_unknown_toolset_operations(
    contract: InstructionContract,
    manifest: AgentManifestContract,
) -> list[str]:
    """Return namespaced toolset operations used in examples but not declared by the agent."""
    known_operations = set(manifest.toolset_operations)
    return sorted(operation for operation in contract.tool_calls if "." in operation and operation not in known_operations)


def validate_instruction_contract(
    contract: InstructionContract,
    *,
    rule_set: InstructionRuleSet,
    known_agents: set[str],
    manifest: AgentManifestContract | None = None,
) -> list[InstructionValidationFinding]:
    """Validate one instruction file against the shared rule set and local inventory."""
    findings: list[InstructionValidationFinding] = []

    if contract.parse_error:
        findings.append(
            InstructionValidationFinding(
                code="CES_INSTRUCTION_PARSE_ERROR",
                severity="error",
                message=f"Instruction parse error for '{contract.agent_name}': {contract.parse_error}",
                line=1,
            )
        )
        return findings

    structure_rule = rule_set.find_rule(contract.relative_path)
    if structure_rule is None:
        findings.append(
            InstructionValidationFinding(
                code="CES_INSTRUCTION_CONTRACT_MISSING",
                severity="warning",
                message=f"No instruction contract matched '{contract.relative_path}'",
                line=1,
            )
        )
        return findings

    actual_order = contract.section_names_in_order
    allowed_orders = structure_rule.allowed_section_orders
    if actual_order not in allowed_orders:
        expected_order = _best_matching_section_order(actual_order, allowed_orders)
        actual_names = set(actual_order)
        expected_names = set(expected_order)

        for missing in expected_order:
            if missing not in actual_names:
                findings.append(
                    InstructionValidationFinding(
                        code="CES_INSTRUCTION_MISSING_SECTION",
                        severity="error",
                        message=(
                            f"Instruction for '{contract.agent_name}' is missing required <{missing}> "
                            f"section required by contract '{structure_rule.identifier}'"
                        ),
                        line=1,
                    )
                )

        for unexpected in actual_order:
            if unexpected not in expected_names:
                section_instance = contract.sections_by_name[unexpected][0]
                findings.append(
                    InstructionValidationFinding(
                        code="CES_INSTRUCTION_UNEXPECTED_SECTION",
                        severity="error",
                        message=(
                            f"Instruction for '{contract.agent_name}' has unexpected <{unexpected}> "
                            f"section for contract '{structure_rule.identifier}'"
                        ),
                        line=section_instance.start_line,
                    )
                )

        if actual_names == expected_names or len(actual_order) != len(set(actual_order)):
            findings.append(
                InstructionValidationFinding(
                    code="CES_INSTRUCTION_SECTION_ORDER_INVALID",
                    severity="error",
                    message=(
                        f"Instruction for '{contract.agent_name}' has section order {list(actual_order)} "
                        f"but expected one of {list(map(list, allowed_orders))}"
                    ),
                    line=1,
                )
            )

    if structure_rule.require_non_empty_examples and "examples" in actual_order and contract.example_count == 0:
        example_sections = contract.sections_by_name.get("examples")
        findings.append(
            InstructionValidationFinding(
                code="CES_INSTRUCTION_EXAMPLES_EMPTY",
                severity="error",
                message=f"Instruction for '{contract.agent_name}' has an empty <examples> section",
                line=example_sections[0].start_line if example_sections else 1,
            )
        )

    for agent_ref in contract.agent_refs:
        if agent_ref not in known_agents:
            findings.append(
                InstructionValidationFinding(
                    code="CES_INSTRUCTION_AGENT_REF_UNKNOWN",
                    severity="error",
                    message=f"Instruction for '{contract.agent_name}' references unknown agent '{agent_ref}'",
                )
            )

    if manifest is None:
        return findings

    known_direct_tools = set(manifest.direct_tools)
    for tool_ref in contract.direct_tool_refs:
        if tool_ref not in known_direct_tools:
            findings.append(
                InstructionValidationFinding(
                    code="CES_INSTRUCTION_TOOL_REF_UNKNOWN",
                    severity="error",
                    message=f"Instruction for '{contract.agent_name}' references unknown tool '{tool_ref}'",
                )
            )

    known_toolset_names = set(manifest.toolset_names)
    known_operations = set(manifest.toolset_operations)
    for operation in contract.tool_calls:
        if "." in operation:
            toolset_name = operation.split(".", 1)[0]
            if toolset_name not in known_toolset_names:
                findings.append(
                    InstructionValidationFinding(
                        code="CES_INSTRUCTION_TOOLCALL_UNKNOWN_TOOLSET",
                        severity="error",
                        message=(
                            f"Instruction for '{contract.agent_name}' references unknown toolset "
                            f"operation '{operation}'"
                        ),
                    )
                )
            elif operation not in known_operations:
                findings.append(
                    InstructionValidationFinding(
                        code="CES_INSTRUCTION_TOOLCALL_UNKNOWN_OPERATION",
                        severity="error",
                        message=(
                            f"Instruction for '{contract.agent_name}' references undeclared toolset "
                            f"operation '{operation}'"
                        ),
                    )
                )
        elif operation not in known_direct_tools:
            findings.append(
                InstructionValidationFinding(
                    code="CES_INSTRUCTION_TOOLCALL_UNKNOWN_TOOL",
                    severity="error",
                    message=(
                        f"Instruction for '{contract.agent_name}' references unknown direct tool "
                        f"call '{operation}'"
                    ),
                )
            )

    return findings


def _best_matching_section_order(
    actual_order: tuple[str, ...],
    allowed_orders: tuple[tuple[str, ...], ...],
) -> tuple[str, ...]:
    best_order = allowed_orders[0]
    best_score: tuple[int, int, int] | None = None
    actual_names = set(actual_order)
    for candidate in allowed_orders:
        candidate_names = set(candidate)
        missing_count = len(candidate_names - actual_names)
        unexpected_count = len(actual_names - candidate_names)
        prefix_match = _common_prefix_length(actual_order, candidate)
        score = (missing_count + unexpected_count, -prefix_match, abs(len(candidate) - len(actual_order)))
        if best_score is None or score < best_score:
            best_score = score
            best_order = candidate
    return best_order


def _common_prefix_length(left: tuple[str, ...], right: tuple[str, ...]) -> int:
    length = 0
    for left_item, right_item in zip(left, right):
        if left_item != right_item:
            break
        length += 1
    return length


def _find_last_open_section(open_sections: list[tuple[str, int]], name: str) -> int:
    for index in range(len(open_sections) - 1, -1, -1):
        if open_sections[index][0] == name:
            return index
    return -1


def _is_inside_section(open_sections: list[tuple[str, int]], section_name: str) -> bool:
    return any(name == section_name for name, _ in open_sections)


def _is_likely_tool_call(operation: str) -> bool:
    false_positives = {
        "e",
        "g",
        "name",
        "step",
        "action",
        "trigger",
        "subtask",
        "user",
        "agent",
        "example",
        "tool_call",
        "i",
        "s",
        "t",
    }
    if operation.lower() in false_positives:
        return False
    if "." in operation:
        return True
    return len(operation) >= 3 and re.search(r"[a-z]", operation) and re.search(r"[A-Z_]", operation)
