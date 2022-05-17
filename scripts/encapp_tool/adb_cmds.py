#!/usr/bin/env python3
import re
from subprocess import PIPE, Popen, SubprocessError
from typing import Dict, Optional, Tuple

ENCAPP_OUTPUT_FILE_NAME_RE = r"encapp_.*"


def run_cmd(cmd: str, debug: int = 0) -> Tuple[bool, str, str]:
    """Run sh command

    Args:
        cmd (str): Command string to be executed by subprocess
        debug (int): Debug level from 0 (No debug)

    Returns:
        Tuple with boolean (True cmd execution succeeded, false otherwise)
        stdout and stderr messages.
    """
    try:
        if debug > 0:
            print(cmd, sep=" ")
        process = Popen(cmd, shell=True, stdout=PIPE, stderr=PIPE)  # noqa: P204
        stdout, stderr = process.communicate()
        ret = True if process.returncode == 0 else False
    except SubprocessError:
        print("Failed to run command: " + cmd)
        return False, "", ""

    return ret, stdout.decode(), stderr.decode()


def get_device_info(serial_inp: Optional[str], debug=0) -> Tuple[Dict, str]:
    """Get android device information for an specific device

    Get device information by executing and parsing command adb devices -l,
    if not serial input specified and only one device connected, it returns
    that device information.If serial input device not connected or none device
    is connected, it fails.

    Args:
        serial_inp (str): Expected serial number to analyze.
        debug (): Debug level

    Returns:
        device_info, serial; Where device info is a map with device info
        and serial is the serial no. of the device.
    """
    device_info = get_connected_devices(debug)
    assert len(device_info) > 0, "error: no devices connected"
    if debug > 2:
        print(f"available devices: {device_info}")

    # select output device
    if serial_inp is None:
        # if user did not select a serial_inp, make sure there is only one
        # device available
        assert len(device_info) == 1, f"error: need to choose a device [{', '.join(device_info.keys())}]"
        serial = list(device_info.keys())[0]
        model = device_info[serial]

    else:
        # if user forced a serial number, make sure it is available
        assert serial_inp in device_info, f"error: device {serial_inp} not available"
        serial = serial_inp
        model = device_info[serial]

    if debug > 0:
        print(f"selecting device: serial: {serial} model: {model}")

    return model, serial


def remove_files_using_regex(
    serial: str, regex_str: str, location: str, debug: int
) -> None:
    """Remove files from an android device specific path following regex.

    Args:
        serial (str): Android device serial no.
        regex_str (str): Regex to match file string
        location (str): Path/directory to analyze and remove files from
        debug (int): Debug level
    """
    adb_cmd = "adb -s " + serial + " shell ls " + location
    _, stdout, _ = run_cmd(adb_cmd, debug)
    output_files = re.findall(regex_str, stdout, re.MULTILINE)
    for file in output_files:
        # remove the output
        adb_cmd = "adb -s " + serial + " shell rm " + location + file
        run_cmd(adb_cmd, debug)


def get_connected_devices(debug: int) -> Dict:
    """Get adb connected devices

    Get adb connected devices info by running adb devices -l

    Args:
        debug (int): Debug level

    Returns:
        Map of found connected devices through adb, with serial no.
        as key.
    """
    # list all available devices
    adb_cmd = "adb devices -l"
    ret, stdout, _ = run_cmd(adb_cmd, debug)
    assert ret, "error: failed to get adb devices"
    # parse list
    device_info = {}
    for line in stdout.split("\n"):
        if line in ["List of devices attached", ""]:
            continue
        serial = line.split()[0]
        item_dict = {}
        for item in line.split()[1:]:
            # ':' used to separate key/values
            if ":" in item:
                key, val = item.split(":", 1)
                item_dict[key] = val
        # ensure the 'model' field exists
        if "model" not in item_dict:
            item_dict["model"] = "generic"
        device_info[serial] = item_dict
    return device_info