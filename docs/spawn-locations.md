# The 100 starting locations

New **Real World** worlds choose one entry uniformly at random. Each has a 1% chance; repeats between different worlds are possible. The mod saves the chosen coordinate pair in the world generator. It does not reroll on login, death, returning from Street View, or updating the mod.

This is a curated landmark list, not a measured popularity ranking. Coordinates are approximate viewpoints. Google coverage and account access vary; these are not 100 individually certified spawn sites. Missing map coverage still follows the normal loading error path, without silently relocating existing terrain. [Provider coverage](https://developers.google.com/maps/coverage).

- `/earth setlocation random`: use this catalogue for future worlds (operator permission).
- `/earth setlocation <latitude> <longitude>`: use a fixed start for future worlds.
- `/earth goto <latitude>, <longitude>`: travel within your existing world.

Old saves retain their geography. Old configs with a customized fixed location keep that choice. Old Palace defaults upgrade to random. `randomSpawn` in `config/realworldmc.json` controls the new-world default.

Both Minecraft builds compile the same `shared/src/main/java/dev/realworldmc/SpawnLocations.java`. To revise the list, keep exactly 100 unique entries, update this table and run `spawnLocationChecks` and the stable GameTests.

| # | Place | Latitude | Longitude |
|---|---|---|---|
| 1 | Palace of Parliament, Bucharest, Romania | 44.427454 | 26.087287 |
| 2 | Times Square, New York, USA | 40.7580 | -73.9855 |
| 3 | Bethesda Terrace, Central Park, New York, USA | 40.7741 | -73.9708 |
| 4 | Brooklyn Bridge Park, New York, USA | 40.7024 | -73.9957 |
| 5 | Golden Gate Bridge, Battery Spencer, USA | 37.8270 | -122.4817 |
| 6 | Alamo Square, San Francisco, USA | 37.7763 | -122.4328 |
| 7 | Griffith Observatory, Los Angeles, USA | 34.1184 | -118.3004 |
| 8 | Santa Monica Pier, California, USA | 34.0100 | -118.4955 |
| 9 | Bellagio Fountains, Las Vegas, USA | 36.1128 | -115.1733 |
| 10 | Millennium Park, Chicago, USA | 41.8826 | -87.6226 |
| 11 | Space Needle, Seattle, USA | 47.6205 | -122.3509 |
| 12 | Lincoln Memorial, Washington DC, USA | 38.8893 | -77.0499 |
| 13 | Boston Common, Boston, USA | 42.3550 | -71.0656 |
| 14 | Ocean Drive, Miami Beach, USA | 25.7772 | -80.1312 |
| 15 | Jackson Square, New Orleans, USA | 29.9574 | -90.0629 |
| 16 | Balboa Park, San Diego, USA | 32.7310 | -117.1490 |
| 17 | Pioneer Courthouse Square, Portland, USA | 45.5189 | -122.6793 |
| 18 | Independence Hall, Philadelphia, USA | 39.9489 | -75.1500 |
| 19 | Diamond Head, Honolulu, USA | 21.2620 | -157.8058 |
| 20 | Mather Point, Grand Canyon, USA | 36.0610 | -112.1070 |
| 21 | Tunnel View, Yosemite, USA | 37.7157 | -119.6769 |
| 22 | Canyon Overlook, Zion, USA | 37.2133 | -112.9407 |
| 23 | Sunset Point, Bryce Canyon, USA | 37.6227 | -112.1653 |
| 24 | Monument Valley viewpoint, USA | 36.9823 | -110.1126 |
| 25 | Hoover Dam overlook, USA | 36.0160 | -114.7410 |
| 26 | Queen Victoria Park, Niagara Falls, Canada | 43.0830 | -79.0790 |
| 27 | CN Tower, Toronto, Canada | 43.6424 | -79.3871 |
| 28 | Stanley Park, Vancouver, Canada | 49.2989 | -123.1417 |
| 29 | Old Port, Montreal, Canada | 45.5075 | -73.5517 |
| 30 | Chateau Frontenac, Quebec City, Canada | 46.8119 | -71.2043 |
| 31 | Westminster, London, UK | 51.5007 | -0.1246 |
| 32 | Tower Bridge, London, UK | 51.5055 | -0.0754 |
| 33 | Edinburgh Castle esplanade, Scotland, UK | 55.9486 | -3.1980 |
| 34 | Royal Crescent, Bath, UK | 51.3860 | -2.3688 |
| 35 | St Stephen's Green, Dublin, Ireland | 53.3382 | -6.2591 |
| 36 | Eiffel Tower, Paris, France | 48.8584 | 2.2945 |
| 37 | Louvre courtyard, Paris, France | 48.8606 | 2.3376 |
| 38 | Mont Saint-Michel, France | 48.6350 | -1.5110 |
| 39 | Promenade des Anglais, Nice, France | 43.6947 | 7.2656 |
| 40 | Place Bellecour, Lyon, France | 45.7579 | 4.8320 |
| 41 | Old Port, Marseille, France | 43.2951 | 5.3740 |
| 42 | Sagrada Familia, Barcelona, Spain | 41.4036 | 2.1744 |
| 43 | Plaza Mayor, Madrid, Spain | 40.4154 | -3.7074 |
| 44 | Plaza de Espana, Seville, Spain | 37.3772 | -5.9869 |
| 45 | City of Arts and Sciences, Valencia, Spain | 39.4549 | -0.3505 |
| 46 | Alhambra, Granada, Spain | 37.1761 | -3.5881 |
| 47 | Belem Tower, Lisbon, Portugal | 38.6916 | -9.2159 |
| 48 | Dom Luis I Bridge, Porto, Portugal | 41.1390 | -8.6095 |
| 49 | Colosseum, Rome, Italy | 41.8902 | 12.4922 |
| 50 | St Peter's Square, Vatican City | 41.9022 | 12.4573 |
| 51 | St Mark's Square, Venice, Italy | 45.4342 | 12.3388 |
| 52 | Piazza del Duomo, Florence, Italy | 43.7731 | 11.2555 |
| 53 | Piazza del Duomo, Milan, Italy | 45.4641 | 9.1900 |
| 54 | Leaning Tower, Pisa, Italy | 43.7230 | 10.3966 |
| 55 | Lungomare, Naples, Italy | 40.8291 | 14.2477 |
| 56 | Lake Como waterfront, Como, Italy | 45.8150 | 9.0820 |
| 57 | Amalfi waterfront, Italy | 40.6340 | 14.6027 |
| 58 | Vernazza, Cinque Terre, Italy | 44.1350 | 9.6837 |
| 59 | Seceda ridge, Dolomites, Italy | 46.6002 | 11.7254 |
| 60 | Gornergrat, Matterhorn view, Switzerland | 45.9839 | 7.7848 |
| 61 | Chapel Bridge, Lucerne, Switzerland | 47.0517 | 8.3073 |
| 62 | Lindenhof, Zurich, Switzerland | 47.3730 | 8.5401 |
| 63 | Jet d'Eau promenade, Geneva, Switzerland | 46.2079 | 6.1550 |
| 64 | Hohematte, Interlaken, Switzerland | 46.6863 | 7.8632 |
| 65 | Schonbrunn Palace, Vienna, Austria | 48.1845 | 16.3122 |
| 66 | Mirabell Gardens, Salzburg, Austria | 47.8054 | 13.0411 |
| 67 | Hallstatt waterfront, Austria | 47.5622 | 13.6493 |
| 68 | Golden Roof, Innsbruck, Austria | 47.2685 | 11.3933 |
| 69 | Marienplatz, Munich, Germany | 48.1372 | 11.5756 |
| 70 | Brandenburg Gate, Berlin, Germany | 52.5163 | 13.3777 |
| 71 | Speicherstadt, Hamburg, Germany | 53.5434 | 9.9906 |
| 72 | Cologne Cathedral square, Germany | 50.9413 | 6.9583 |
| 73 | Heidelberg Castle terrace, Germany | 49.4106 | 8.7154 |
| 74 | Neuschwanstein Castle viewpoint, Germany | 47.5576 | 10.7498 |
| 75 | Dam Square, Amsterdam, Netherlands | 52.3731 | 4.8922 |
| 76 | Erasmus Bridge, Rotterdam, Netherlands | 51.9090 | 4.4872 |
| 77 | Grand Place, Brussels, Belgium | 50.8467 | 4.3525 |
| 78 | Markt, Bruges, Belgium | 51.2089 | 3.2242 |
| 79 | Old Town Square, Prague, Czechia | 50.0875 | 14.4213 |
| 80 | Fisherman's Bastion, Budapest, Hungary | 47.5020 | 19.0348 |
| 81 | Nyhavn, Copenhagen, Denmark | 55.6796 | 12.5918 |
| 82 | Stortorget, Stockholm, Sweden | 59.3258 | 18.0716 |
| 83 | Opera House waterfront, Oslo, Norway | 59.9074 | 10.7530 |
| 84 | Bryggen, Bergen, Norway | 60.3972 | 5.3244 |
| 85 | Senate Square, Helsinki, Finland | 60.1695 | 24.9523 |
| 86 | Preseren Square, Ljubljana, Slovenia | 46.0514 | 14.5060 |
| 87 | Old Town, Dubrovnik, Croatia | 42.6410 | 18.1100 |
| 88 | Diocletian's Palace, Split, Croatia | 43.5081 | 16.4402 |
| 89 | Acropolis viewpoint, Athens, Greece | 37.9715 | 23.7257 |
| 90 | Oia, Santorini, Greece | 36.4618 | 25.3753 |
| 91 | Shibuya Crossing, Tokyo, Japan | 35.6595 | 139.7005 |
| 92 | Senso-ji, Tokyo, Japan | 35.7148 | 139.7967 |
| 93 | Kiyomizu-dera, Kyoto, Japan | 34.9948 | 135.7850 |
| 94 | Osaka Castle park, Japan | 34.6873 | 135.5262 |
| 95 | Peace Memorial Park, Hiroshima, Japan | 34.3927 | 132.4526 |
| 96 | Opera House forecourt, Sydney, Australia | -33.8575 | 151.2145 |
| 97 | Federation Square, Melbourne, Australia | -37.8179 | 144.9690 |
| 98 | South Bank, Brisbane, Australia | -27.4783 | 153.0231 |
| 99 | Kings Park, Perth, Australia | -31.9617 | 115.8436 |
| 100 | Sky Tower plaza, Auckland, New Zealand | -36.8485 | 174.7622 |
