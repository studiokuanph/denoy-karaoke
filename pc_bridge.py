"""
Denoy Karaoke Bridge Server
Run this on your PC while MegaOke is running.
Connect your Android app to your PC's IP address.

Usage:
  1. Make sure MegaOke is running
  2. Run: python pc_bridge.py
  3. Note the IP address shown
  4. Open Android app -> Remote tab -> enter IP
"""
import asyncio
import json
import websockets
import socket

MEGAOKE_PORT = 48080
BRIDGE_PORT = 8765

def get_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except:
        return "127.0.0.1"
    finally:
        s.close()

async def bridge(android_ws, path):
    print(f"Android device connected")
    try:
        async with websockets.connect(f"ws://127.0.0.1:{MEGAOKE_PORT}/") as mega:
            # Forward welcome message
            welcome = await mega.recv()
            await android_ws.send(welcome)
            
            async def mega_to_android():
                async for msg in mega:
                    try:
                        await android_ws.send(msg)
                    except:
                        break
            
            async def android_to_mega():
                async for msg in android_ws:
                    try:
                        await mega.send(msg)
                    except:
                        break
            
            await asyncio.gather(mega_to_android(), android_to_mega())
    except Exception as e:
        try:
            await android_ws.send(json.dumps({
                "type": "error",
                "message": f"Cannot connect to MegaOke: {e}"
            }))
        except:
            pass
    print("Android device disconnected")

async def main():
    ip = get_ip()
    print("=" * 50)
    print("  Denoy Karaoke Bridge Server")
    print("=" * 50)
    print(f"  PC IP address: {ip}")
    print(f"  Bridge port:   {BRIDGE_PORT}")
    print()
    print(f"  Make sure MegaOke is running!")
    print()
    print(f"  In the Android app -> Remote tab,")
    print(f"  enter IP: {ip}  Port: {BRIDGE_PORT}")
    print("=" * 50)
    print()
    
    async with websockets.serve(bridge, "0.0.0.0", BRIDGE_PORT):
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())
