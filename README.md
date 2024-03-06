# CSVExport
A plugin to extract key information out of the game as csv for analysis in Excel/Tableau/PowerBI

## Usage
Use the config to select an output dir, defaults to home/user. Each tick, the game will write 4 .csv files:

tile_data: info on items around your character with GE prices
player_data: key player metrics such as stats, health, location

items_data: info on inventory and equipped items

A new file will be created each day as to avoid file bloat