"""Validate a caller-selected adb device identifier without binding tools to one machine."""
import argparse

def explicit_serial(value: str) -> str:
    if not value or value.startswith('-') or any(char.isspace() for char in value):
        raise argparse.ArgumentTypeError('Use the exact nonempty identifier from adb devices (USB, emulator, or Wi-Fi).')
    return value
