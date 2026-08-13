# Description

This mod adds MCP (Model Context Protocol) support for Minecraft, allowing AIs (eg, ChatBot, Agent, Local Models) to interact with the game, entities and players.


<details>
<summary>This allows AI models to perform tasks using these tools:</summary>
  
- get_block
- get_blocks_area
- set_block
- fill_blocks
- break_block
- get_biome
- get_heightmap
- get_light_level
- get_player_info
- teleport_player
- move_player
- look_at
- get_inventory
- select_hotbar_slot
- give_item
- clear_inventory
- get_nearby_entities
- spawn_entity
- interact_with_entity
- execute_command
- send_chat
- get_chat_history
- get_world_info
- set_time
- set_weather
- scan_surroundings
- build_schematic
- build_sphere
- build_cylinder

</details>


# In Game Commands

These commands can be used in game to see some info:

- `/mcp status`
- `/mcp start`
- `/mcp stop`
- `/mcp restart`
- `/mcp token [show|generate]` **(Only when Authentication is enabled)**
- `/mcp config [port|requireAuth|broadcast|readOnly] [value]`
- `/mcp help`


# Configuration File

minecraft-mcp.json
```json
{
  "enabled": true,
  "port": 25560,
  "host": "127.0.0.1",
  "requireAuth": false,
  "authToken": "",
  "allowCommandExecution": true,
  "allowWorldModification": true,
  "broadcastToChat": true,
  "readOnlyMode": false,
  "maxBlocksPerOperation": 32768
}
```




# Integration

**1. Claude (Code & Desktop), VS Code Extensions (eg, Cline, Roo)**


```json
{
  "mcpServers": {
    "minecraft": {
      "url": "http://127.0.0.1:25560/sse",
      "transport": "sse"
    }
  }
}
```


**2. Cursor, Codex CLI**


```json
{
  "mcpServers": {
    "minecraft": {
      "url": "http://127.0.0.1:25560/sse"
    }
  }
}
```

**3. Antigravity (IDE & CLI), Windsurf, Codeium, VSCodium**


```json
{
  "mcpServers": {
    "minecraft": {
      "serverUrl": "http://127.0.0.1:25560/sse"
    }
  }
}
```




**4. Direct HTTP and Custom Scripts/Tools**


cURL / Rest

```bash
curl -X POST http://127.0.0.1:25560/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_player_status","arguments":{}}}'
```


Python (Need to use MCP SDK)


```python
import asyncio
from mcp import ClientSession
from mcp.client.sse import sse_client

async def main():
    async with sse_client("http://127.0.0.1:25560/sse") as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            tools = await session.list_tools()
            print([t.name for t in tools.tools])

asyncio.run(main())
```
