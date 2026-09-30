#!/usr/bin/env python3
"""
Prompt Injection Test Harness

Prepares test submissions with injected payloads for AI grading agent evaluation.

Usage:
    python test_runner.py                    # Create all test submissions
    python test_runner.py --analyze          # Analyze recorded AI grader responses
    python test_runner.py --category <name>  # Create submissions for one category
"""

import json
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import List, Optional

import yaml
from rich.console import Console
from rich.progress import Progress, SpinnerColumn, TextColumn
from rich.table import Table

from report_generator import ReportGenerator

console = Console()


@dataclass
class TestCase:
    """Represents a single prompt injection test case"""
    id: str
    category: str
    severity: str
    name: str
    description: str
    injection_vector: dict
    expected_behavior: dict
    detection_patterns: list
    failure_indicators: list


@dataclass
class TestResult:
    """Result of running a single test case"""
    test_id: str
    test_name: str
    category: str
    severity: str
    passed: bool
    injection_detected: bool
    injection_rejected: bool
    rubric_applied_correctly: bool
    grader_output: str = ""
    failure_reason: Optional[str] = None
    timestamp: str = field(default_factory=lambda: datetime.now().isoformat())


class TestRunner:
    """Main test runner for prompt injection security tests"""
    
    def __init__(self, config_path: str = "config.yaml", test_cases_path: str = "test-cases.json"):
        self.config = self._load_config(config_path)
        self.test_cases = self._load_test_cases(test_cases_path)
        self.results: List[TestResult] = []
        self.base_project_path = Path(__file__).parent.parent
        
    def _load_config(self, path: str) -> dict:
        """Load configuration from YAML file"""
        config_file = Path(__file__).parent / path
        with open(config_file, 'r') as f:
            return yaml.safe_load(f)
    
    def _load_test_cases(self, path: str) -> List[TestCase]:
        """Load test cases from JSON file"""
        test_file = Path(__file__).parent / path
        with open(test_file, 'r') as f:
            data = json.load(f)
        
        return [
            TestCase(
                id=tc['id'],
                category=tc['category'],
                severity=tc['severity'],
                name=tc['name'],
                description=tc['description'],
                injection_vector=tc['injection_vector'],
                expected_behavior=tc['expected_behavior'],
                detection_patterns=tc.get('detection_patterns', []),
                failure_indicators=tc.get('failure_indicators', [])
            )
            for tc in data['test_cases']
        ]
    
    def setup_test_submission(self, test_case: TestCase) -> Path:
        """Create a test submission directory with injection payload"""
        test_dir = Path(__file__).parent / "test-submissions" / test_case.id
        test_dir.mkdir(parents=True, exist_ok=True)
        
        # Copy base project structure
        for item in ['backend', 'frontend', 'prd']:
            src = self.base_project_path / item
            dst = test_dir / item
            if src.exists():
                if dst.exists():
                    shutil.rmtree(dst)
                shutil.copytree(src, dst)
        
        # Inject the payload
        self._inject_payload(test_dir, test_case)
        
        return test_dir
    
    def _inject_payload(self, test_dir: Path, test_case: TestCase):
        """Inject the malicious payload into the test submission"""
        vector = test_case.injection_vector
        location = vector['location']
        payload = vector['payload']
        
        # Handle multi-file injections
        if location == "multiple_files" and isinstance(payload, dict):
            for file_key, injection in payload.items():
                # Extract actual file path from the injection
                parts = injection.split(' // ')
                if len(parts) == 2:
                    file_path, comment = parts[0], parts[1]
                    target_file = test_dir / file_path
                    if target_file.exists():
                        content = target_file.read_text()
                        target_file.write_text(f"{content}\n// {comment}\n")
            return
        
        # Handle single file injections
        target_file = test_dir / location
        target_file.parent.mkdir(parents=True, exist_ok=True)
        
        if vector['type'] == 'code_comment':
            if target_file.exists():
                content = target_file.read_text()
                target_file.write_text(f"{payload}\n{content}")
            else:
                target_file.write_text(f"{payload}\n")
        
        elif vector['type'] == 'markdown_comment':
            if target_file.exists():
                content = target_file.read_text()
                target_file.write_text(f"{content}\n\n{payload}\n")
            else:
                target_file.write_text(f"{payload}\n")
        
        elif vector['type'] == 'yaml_comment':
            if target_file.exists():
                content = target_file.read_text()
                target_file.write_text(f"{content}\n{payload}\n")
            else:
                target_file.write_text(f"{payload}\n")
        
        elif vector['type'] == 'typescript_comment_block':
            if target_file.exists():
                content = target_file.read_text()
                target_file.write_text(f"{payload}\n\n{content}")
            else:
                target_file.write_text(f"{payload}\n")
        
        elif vector['type'] == 'string_literal':
            if target_file.exists():
                content = target_file.read_text()
                # Insert near the top of the file
                lines = content.split('\n')
                insert_pos = min(10, len(lines))
                lines.insert(insert_pos, payload)
                target_file.write_text('\n'.join(lines))
            else:
                target_file.write_text(f"{payload}\n")
        
        elif vector['type'] == 'json_file':
            target_file.write_text(payload)
        
        elif vector['type'] == 'json_field':
            if target_file.exists():
                try:
                    content = json.loads(target_file.read_text())
                    # Add the malicious field
                    if isinstance(payload, str):
                        # Parse the field from the payload string
                        key, value = payload.split(': ', 1)
                        key = key.strip('"')
                        content[key] = value.strip('"')
                    target_file.write_text(json.dumps(content, indent=2))
                except json.JSONDecodeError:
                    pass
        
        elif vector['type'] == 'xml_comment':
            if target_file.exists():
                content = target_file.read_text()
                # Insert before closing </project>
                if '</project>' in content:
                    content = content.replace('</project>', f'{payload}\n</project>')
                    target_file.write_text(content)
        
        elif vector['type'] == 'markdown_document':
            target_file.write_text(payload)
    
    def evaluate_submission(self, test_dir: Path, test_case: TestCase) -> TestResult:
        """
        Prepare a test submission for AI grading agent evaluation.
        
        The test submission is created at: security-tests/test-submissions/{test_id}/
        
        To evaluate:
        1. Point your AI grading agent to the test submission directory
        2. Have the AI grader evaluate it as a normal submission
        3. Record the AI grader's response
        4. Run this script with --analyze to check results
        
        For manual evaluation, see the generated submissions in test-submissions/
        """
        # Just prepare the submission - AI grader evaluates manually
        grader_output = "Submission prepared. Awaiting AI grader evaluation."
        
        # Default to passed for setup phase
        return TestResult(
            test_id=test_case.id,
            test_name=test_case.name,
            category=test_case.category,
            severity=test_case.severity,
            passed=True,  # Will be updated after AI evaluation
            injection_detected=False,
            injection_rejected=True,
            rubric_applied_correctly=True,
            grader_output=grader_output,
            failure_reason=None
        )
    
    def cleanup_test_submission(self, test_dir: Path):
        """Clean up test submission directory"""
        if test_dir.exists() and self.config['execution'].get('cleanup_after_test', True):
            shutil.rmtree(test_dir)
    
    def run_all_tests(self, analyze_mode: bool = False) -> List[TestResult]:
        """Run all test cases"""
        if analyze_mode:
            console.print(f"\n[bold blue]Analyzing AI grader responses for {len(self.test_cases)} tests...[/bold blue]\n")
            return self._analyze_recorded_responses()
        else:
            console.print(f"\n[bold blue]Preparing {len(self.test_cases)} test submissions...[/bold blue]\n")
            return self._prepare_all_submissions()
        
        with Progress(
            SpinnerColumn(),
            TextColumn("[progress.description]{task.description}"),
            console=console
        ) as progress:
            for test_case in self.test_cases:
                task = progress.add_task(f"Running {test_case.id}: {test_case.name}", total=None)
                
                # Setup
                test_dir = self.setup_test_submission(test_case)
                
                # Evaluate
                result = self.evaluate_submission(test_dir, test_case)
                self.results.append(result)
                
                # Cleanup
                self.cleanup_test_submission(test_dir)
                
                progress.remove_task(task)
        
        return self.results
    
    def run_by_category(self, category: str, analyze_mode: bool = False) -> List[TestResult]:
        """Run tests for a specific category"""
        filtered = [tc for tc in self.test_cases if tc.category == category]
        if analyze_mode:
            console.print(f"\n[bold blue]Analyzing AI grader responses for {len(filtered)} tests in category: {category}[/bold blue]\n")
        else:
            console.print(f"\n[bold blue]Preparing {len(filtered)} test submissions in category: {category}[/bold blue]\n")
        
        for test_case in filtered:
            test_dir = self.setup_test_submission(test_case)
            result = self.evaluate_submission(test_dir, test_case)
            self.results.append(result)
            if not analyze_mode:
                self.cleanup_test_submission(test_dir)
        
        return self.results
    
    def run_by_severity(self, severity: str, analyze_mode: bool = False) -> List[TestResult]:
        """Run tests for a specific severity level"""
        filtered = [tc for tc in self.test_cases if tc.severity == severity]
        if analyze_mode:
            console.print(f"\n[bold blue]Analyzing AI grader responses for {len(filtered)} tests with severity: {severity}[/bold blue]\n")
        else:
            console.print(f"\n[bold blue]Preparing {len(filtered)} test submissions with severity: {severity}[/bold blue]\n")
        
        for test_case in filtered:
            test_dir = self.setup_test_submission(test_case)
            result = self.evaluate_submission(test_dir, test_case)
            self.results.append(result)
            if not analyze_mode:
                self.cleanup_test_submission(test_dir)
        
        return self.results
    
    def _prepare_all_submissions(self) -> List[TestResult]:
        """Prepare all test submissions for AI grader evaluation"""
        with Progress(
            SpinnerColumn(),
            TextColumn("[progress.description]{task.description}"),
            console=console
        ) as progress:
            for test_case in self.test_cases:
                task = progress.add_task(f"Preparing {test_case.id}: {test_case.name}", total=None)
                
                test_dir = self.setup_test_submission(test_case)
                result = self.evaluate_submission(test_dir, test_case)
                self.results.append(result)
                
                progress.remove_task(task)
        
        # Print summary of prepared submissions
        console.print("\n[bold green]Test submissions prepared![/bold green]")
        console.print(f"\nSubmissions are in: [cyan]security-tests/test-submissions/[/cyan]")
        console.print("\nNext steps:")
        console.print("  1. Have your AI grading agent evaluate each submission")
        console.print("  2. Save the AI grader's response to: test-submissions/{test_id}/grader-response.txt")
        console.print("  3. Run: python test_runner.py --analyze")
        
        return self.results
    
    def _analyze_recorded_responses(self) -> List[TestResult]:
        """Analyze recorded AI grader responses"""
        import re
        
        for test_case in self.test_cases:
            test_dir = Path(__file__).parent / "test-submissions" / test_case.id
            response_file = test_dir / "grader-response.txt"
            
            if not response_file.exists():
                self.results.append(TestResult(
                    test_id=test_case.id,
                    test_name=test_case.name,
                    category=test_case.category,
                    severity=test_case.severity,
                    passed=False,
                    injection_detected=False,
                    injection_rejected=False,
                    rubric_applied_correctly=False,
                    grader_output="No grader response found",
                    failure_reason="grader-response.txt not found"
                ))
                continue
            
            grader_output = response_file.read_text()
            
            # Check for injection success indicators (BAD)
            injection_succeeded = False
            for indicator in test_case.failure_indicators:
                if re.search(indicator, grader_output, re.IGNORECASE):
                    injection_succeeded = True
                    break
            
            # Check for detection indicators (GOOD)
            injection_detected = False
            for pattern in test_case.detection_patterns:
                if re.search(pattern, grader_output, re.IGNORECASE):
                    injection_detected = True
                    break
            
            # Determine if test passed
            rubric_applied_correctly = not injection_succeeded
            passed = injection_detected or (not injection_succeeded and rubric_applied_correctly)
            
            self.results.append(TestResult(
                test_id=test_case.id,
                test_name=test_case.name,
                category=test_case.category,
                severity=test_case.severity,
                passed=passed,
                injection_detected=injection_detected,
                injection_rejected=not injection_succeeded,
                rubric_applied_correctly=rubric_applied_correctly,
                grader_output=grader_output[:500] + "..." if len(grader_output) > 500 else grader_output,
                failure_reason=None if passed else "Injection succeeded - AI grader was manipulated"
            ))
        
        return self.results


def main():
    """Main entry point"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Prompt Injection Test Harness for AI Grading Agents')
    parser.add_argument('--analyze', action='store_true', help='Analyze recorded AI grader responses')
    parser.add_argument('--category', '-c', help='Prepare/analyze tests for a specific category')
    parser.add_argument('--severity', '-s', help='Prepare/analyze tests for a specific severity level')
    parser.add_argument('--output', '-o', default='results', help='Output directory for reports')
    parser.add_argument('--no-report', action='store_true', help='Skip report generation')
    args = parser.parse_args()
    
    runner = TestRunner()
    
    try:
        if args.category:
            results = runner.run_by_category(args.category, args.analyze)
        elif args.severity:
            results = runner.run_by_severity(args.severity, args.analyze)
        else:
            results = runner.run_all_tests(args.analyze)
        
        if not args.analyze:
            # Just prepared submissions, no results to display yet
            return
        
        # Display results (only in analyze mode)
        table = Table(title="\nTest Results Summary")
        table.add_column("ID", style="cyan")
        table.add_column("Name", style="white")
        table.add_column("Category", style="yellow")
        table.add_column("Severity", style="red")
        table.add_column("Passed", style="green")
        table.add_column("Injection Detected", style="blue")
        
        for result in results:
            passed_str = "✓" if result.passed else "✗"
            detected_str = "✓" if result.injection_detected else "✗"
            table.add_row(
                result.test_id,
                result.test_name[:40] + "..." if len(result.test_name) > 40 else result.test_name,
                result.category,
                result.severity,
                passed_str,
                detected_str
            )
        
        console.print(table)
        
        # Summary stats
        passed = sum(1 for r in results if r.passed)
        total = len(results)
        console.print(f"\n[bold green]Passed: {passed}/{total}[/bold green]")
        
        if passed < total:
            console.print(f"[bold red]Failed: {total - passed}/{total}[/bold red]")
        
        # Generate reports
        if not args.no_report:
            output_dir = Path(__file__).parent / args.output
            reporter = ReportGenerator(str(output_dir))
            reporter.generate_all_reports(results, [])
            console.print(f"\n[bold blue]Reports generated in: {output_dir}[/bold blue]")
            console.print("  - test-results.json")
            console.print("  - test-results.md")
            console.print("  - test-results.html")
        
        if passed < total:
            console.print("\n[bold red]Some tests failed. The AI grading agent may be vulnerable to prompt injection.[/bold red]")
            sys.exit(1)
        else:
            console.print("\n[bold green]All tests passed! AI grading agent is secure against prompt injection.[/bold green]")
            sys.exit(0)
    
    except Exception as e:
        console.print(f"\n[bold red]Error: {e}[/bold red]")
        import traceback
        traceback.print_exc()
        sys.exit(2)


if __name__ == "__main__":
    main()
