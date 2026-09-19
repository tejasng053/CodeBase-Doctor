import importlib.util
from pathlib import Path
import tempfile
import subprocess
import sys
import unittest

spec = importlib.util.spec_from_file_location('doctor_dev', Path(__file__).with_name('dev.py'))
dev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dev)


class LocalConfigurationTest(unittest.TestCase):
    def test_configuration_is_literal_data_and_cannot_override_host_commands(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / '.env'
            marker = Path(temporary) / 'executed'
            payload = '$(touch ' + str(marker) + ')'
            target.write_text('GROQ_API_KEY=' + payload + '\nPATH=/malicious\nDOCTOR_API_TOKEN="abcdefghijklmnopqrstuvwxyz012345"\n')
            values = dev.read_config(target)
            self.assertEqual(values['GROQ_API_KEY'], payload)
            self.assertEqual(values['DOCTOR_API_TOKEN'], 'abcdefghijklmnopqrstuvwxyz012345')
            self.assertNotIn('PATH', values)
            self.assertFalse(marker.exists())

    def test_shutdown_terminates_a_service_process_group(self):
        process = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'], start_new_session=True)
        dev.stop_group(process)
        self.assertIsNotNone(process.poll())


if __name__ == '__main__':
    unittest.main()
