# Earth commands (Minecraft 1.21.1)

Use `/earth` in instructions and examples. `/rw` is an equivalent shortcut on 1.21.1. Both roots work without arguments and share the same actions.

- `/earth help`: short, clickable command list. Click a command to put it in chat.
- `/earth help more`: advanced commands.
- `/earth goto <place or latitude, longitude>`: travel.
- `/earth streetview`: find the closest available photo near your Earth position.
- `/earth back`: leave Street View.
- `/earth setup`: open the local, masked credential setup screen. `/rw setup` also works.

The setup command takes no credentials. Enter keys in the screen, test access, then save. Keys stay in the local service configuration files. Unchanged settings need no restart. If credentials or the folder changed, restart the services named on the result screen, then reopen your world. A full Minecraft restart is not required. This command is client-side and does not send your key to a game server.

The setup screen and command are for 1.21.1. The experimental 26.2 build still uses manual configuration.

Travel progress appears above the screen in a green boss bar. The bar shows the current stage, not an exact download percentage. Street View photo loading and map connection status appear above the hotbar. Errors stay in chat, with a green RW prefix and commands in yellow.
