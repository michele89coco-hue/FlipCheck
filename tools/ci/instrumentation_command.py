"""Build an adb remote-shell command; never log the returned credential-bearing text."""
import os
import shlex


def command(key, test_package):
    return shlex.join([
        "am", "instrument", "-w", "-r", "-e", "live_api_key", key,
        "-e", "class", "com.flipcheck.nativebeta.LiveRegressionSuite",
        test_package + "/androidx.test.runner.AndroidJUnitRunner",
    ])


if __name__ == "__main__":
    print(command(os.environ["FLIPCHECK_LIVE_API_KEY"], os.environ["TEST_PACKAGE"]))
