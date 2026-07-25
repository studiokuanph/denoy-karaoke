"""
Denoy Karaoke PC Bridge Server
Connects Android app to the running MegaOke server.
Run this on the PC while MegaOke is running.

Usage: python pc_bridge.py
"""
import asyncio
import json
import websockets
import os

MEGAOKE_WS = "ws://127.0.0.1:48080/"
BRIDGE_PORT = 8765

connected_clients = set()
megaoke_ws = None

async def forward_to_megaoke(message):
    global megaoke_ws
    if megaoke_ws:
        try:
            await megaoke_ws.send(message)
        except:
            pass

async def handle_client(websocket):
    global megaoke_ws
    connected_clients.add(websocket)
    try:
        # Connect to MegaOke
        async with websockets.connect(MEGAOKE_WS) as mega:
            megaoke_ws = mega
            
            # Forward welcome
            welcome = await mega.recv()
            await websocket.send(welcome)
            
            async def forward_from_mega():
                async for msg in mega:
                    for client in connected_clients.copy():
                        try:
                            await client.send(msg)
                        except:
                            connected_clients.discard(client)
            
            forward_task = asyncio.create_task(forward_from_mega())
            
            # Forward commands from Android app
            async for msg in websocket:
                await mega.send(msg)
            
            forward_task.cancel()
    except Exception as e:
        await websocket.send(json.dumps({"error": str(e)}))
    finally:
        connected_clients.discard(websocket)

async def main():
    print(f"Denoy Karaoke Bridge Server")
    print(f"Make sure MegaOke is running!")
    print(f"Listening on port {BRIDGE_PORT}")
    print(f"Connect Android app to ws://YOUR_PC_IP:{BRIDGE_PORT}")
    print()
    
    async with websockets.serve(handle_client, "0.0.0.0", BRIDGE_PORT):
        await asyncio.Future()  # run forever

if __name__ == "__main__":
    asyncio.run(main())
