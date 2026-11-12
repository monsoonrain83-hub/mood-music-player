import pathlib
import tempfile
import unittest
from summarize_device_qa import battery_fields, summarize


class BatteryReportTest(unittest.TestCase):
    def test_parse_tenths_celsius(self):
        actual = battery_fields("Current Battery Service state:\n  level: 71\n  temperature: 335\n  voltage: 3962\n")
        self.assertEqual(actual["level"], 71)
        self.assertAlmostEqual(actual["temperature_celsius"], 33.5)

    def test_summary_never_invents_battery(self):
        with tempfile.TemporaryDirectory() as directory:
            p = pathlib.Path(directory)
            (p / "battery_before.txt").write_text("level: 90\nplugged: 0\n")
            (p / "battery_after.txt").write_text("level: 85\nplugged: 0\n")
            (p / "process_observations.txt").write_text("1234\n")
            result = summarize(p)
            self.assertEqual(result["battery_change_percentage_points"], -5)
            self.assertEqual(result["samples"], 0)
            self.assertFalse(result["process_missing_observed"])

    def test_missing_values_remain_missing(self):
        self.assertEqual(battery_fields("not a battery report"), {})


if __name__ == "__main__":
    unittest.main()
