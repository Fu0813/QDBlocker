"""Same privacy-limited observer on the app-scoped VPN's SOCKS5 route."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import qd_capture

qd_capture.STATE = qd_capture.ROOT / "work" / "proxy-socks"
addons = qd_capture.addons
