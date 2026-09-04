import shlex
import unittest
from instrumentation_command import command


class RemoteArgumentTest(unittest.TestCase):
    def test_secret_remains_one_remote_argument(self):
        for value in ["example-key", "example-key\n", "a b", "a'b", "$(false); x"]:
            with self.subTest(value=value):
                args = shlex.split(command(value, "example.test"))
                self.assertEqual(args[6], value)
                self.assertEqual(args[7:9], ["-e", "class"])
                self.assertEqual(len(args), 11)


if __name__ == "__main__":
    unittest.main()
