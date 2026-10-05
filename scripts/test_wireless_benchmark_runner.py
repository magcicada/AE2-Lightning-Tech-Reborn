"""Check sample isolation without starting Minecraft."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "benchmark_runner", Path(__file__).with_name("run-wireless-export-benchmark.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class WorldIsolationTest(unittest.TestCase):
    def test_every_sample_uses_a_new_world_and_collects_only_its_report(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            baseline, candidate = root / "baseline", root / "candidate"
            baseline.mkdir()
            candidate.mkdir()
            worlds = []

            def launch(command, **kwargs):
                properties = dict(arg[2:].split("=", 1) for arg in command if arg.startswith("-P"))
                world = Path(properties["ae2ltBenchmarkRunDirectory"])
                self.assertFalse(world.exists(), "a previous JVM's world was reused")
                worlds.append(world)
                reports = world / "benchmark-reports/wireless-interface-io"
                reports.mkdir(parents=True)
                scenario = properties["ae2ltBenchmarkScenario"]
                report = reports / (scenario + ".json")
                report.write_text(json.dumps({"partial": False, "samples": 200, "world": str(world)}))
                report.with_name(report.stem + "-ticks.csv").write_text("tick,ms\n1,1\n")
                kwargs["stdout"].write("7 GAME TESTS COMPLETE\n")
                return subprocess.CompletedProcess(command, 0)

            output = root / "results"
            argv = ["runner", "--baseline", str(baseline), "--candidate", str(candidate),
                    "--output", str(output), "--runs", "2", "--warmup", "40", "--samples", "200"]
            with patch.object(sys, "argv", argv), patch.object(runner, "identity", return_value={"head": "test", "dirty": False}), \
                    patch.object(runner.subprocess, "run", side_effect=launch):
                self.assertEqual(0, runner.main())
            self.assertEqual(8, len(set(worlds)))
            manifest = json.loads((output / "manifest.json").read_text())
            self.assertEqual(["baseline", "candidate", "baseline", "candidate",
                              "candidate", "baseline", "candidate", "baseline"],
                             [run["version"] for run in manifest["runs"]])
            self.assertTrue(all(run["freshWorld"] and run["completeReport"] for run in manifest["runs"]))
            self.assertEqual(8, len(list(output.glob("*/*/*-run*.json"))))
            with patch.object(sys, "argv", argv), self.assertRaises(FileExistsError):
                runner.main()


if __name__ == "__main__":
    unittest.main()
