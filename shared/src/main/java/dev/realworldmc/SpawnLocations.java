package dev.realworldmc;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Shared by both Minecraft builds. Coordinates are approximate outdoor viewpoints. */
public final class SpawnLocations {
    public record Location(String name, double latitude, double longitude) {
        public Location {
            if (!Double.isFinite(latitude) || Math.abs(latitude) > 90
                || !Double.isFinite(longitude) || Math.abs(longitude) > 180)
                throw new IllegalArgumentException("Invalid spawn coordinates for " + name);
        }
    }

    public static final List<Location> ALL = List.of(
        new Location("Palace of Parliament, Bucharest, Romania", 44.427454, 26.087287),
        new Location("Times Square, New York, USA", 40.7580, -73.9855),
        new Location("Bethesda Terrace, Central Park, New York, USA", 40.7741, -73.9708),
        new Location("Brooklyn Bridge Park, New York, USA", 40.7024, -73.9957),
        new Location("Golden Gate Bridge, Battery Spencer, USA", 37.8270, -122.4817),
        new Location("Alamo Square, San Francisco, USA", 37.7763, -122.4328),
        new Location("Griffith Observatory, Los Angeles, USA", 34.1184, -118.3004),
        new Location("Santa Monica Pier, California, USA", 34.0100, -118.4955),
        new Location("Bellagio Fountains, Las Vegas, USA", 36.1128, -115.1733),
        new Location("Millennium Park, Chicago, USA", 41.8826, -87.6226),
        new Location("Space Needle, Seattle, USA", 47.6205, -122.3509),
        new Location("Lincoln Memorial, Washington DC, USA", 38.8893, -77.0499),
        new Location("Boston Common, Boston, USA", 42.3550, -71.0656),
        new Location("Ocean Drive, Miami Beach, USA", 25.7772, -80.1312),
        new Location("Jackson Square, New Orleans, USA", 29.9574, -90.0629),
        new Location("Balboa Park, San Diego, USA", 32.7310, -117.1490),
        new Location("Pioneer Courthouse Square, Portland, USA", 45.5189, -122.6793),
        new Location("Independence Hall, Philadelphia, USA", 39.9489, -75.1500),
        new Location("Diamond Head, Honolulu, USA", 21.2620, -157.8058),
        new Location("Mather Point, Grand Canyon, USA", 36.0610, -112.1070),
        new Location("Tunnel View, Yosemite, USA", 37.7157, -119.6769),
        new Location("Canyon Overlook, Zion, USA", 37.2133, -112.9407),
        new Location("Sunset Point, Bryce Canyon, USA", 37.6227, -112.1653),
        new Location("Monument Valley viewpoint, USA", 36.9823, -110.1126),
        new Location("Hoover Dam overlook, USA", 36.0160, -114.7410),
        new Location("Queen Victoria Park, Niagara Falls, Canada", 43.0830, -79.0790),
        new Location("CN Tower, Toronto, Canada", 43.6424, -79.3871),
        new Location("Stanley Park, Vancouver, Canada", 49.2989, -123.1417),
        new Location("Old Port, Montreal, Canada", 45.5075, -73.5517),
        new Location("Chateau Frontenac, Quebec City, Canada", 46.8119, -71.2043),
        new Location("Westminster, London, UK", 51.5007, -0.1246),
        new Location("Tower Bridge, London, UK", 51.5055, -0.0754),
        new Location("Edinburgh Castle esplanade, Scotland, UK", 55.9486, -3.1980),
        new Location("Royal Crescent, Bath, UK", 51.3860, -2.3688),
        new Location("St Stephen's Green, Dublin, Ireland", 53.3382, -6.2591),
        new Location("Eiffel Tower, Paris, France", 48.8584, 2.2945),
        new Location("Louvre courtyard, Paris, France", 48.8606, 2.3376),
        new Location("Mont Saint-Michel, France", 48.6350, -1.5110),
        new Location("Promenade des Anglais, Nice, France", 43.6947, 7.2656),
        new Location("Place Bellecour, Lyon, France", 45.7579, 4.8320),
        new Location("Old Port, Marseille, France", 43.2951, 5.3740),
        new Location("Sagrada Familia, Barcelona, Spain", 41.4036, 2.1744),
        new Location("Plaza Mayor, Madrid, Spain", 40.4154, -3.7074),
        new Location("Plaza de Espana, Seville, Spain", 37.3772, -5.9869),
        new Location("City of Arts and Sciences, Valencia, Spain", 39.4549, -0.3505),
        new Location("Alhambra, Granada, Spain", 37.1761, -3.5881),
        new Location("Belem Tower, Lisbon, Portugal", 38.6916, -9.2159),
        new Location("Dom Luis I Bridge, Porto, Portugal", 41.1390, -8.6095),
        new Location("Colosseum, Rome, Italy", 41.8902, 12.4922),
        new Location("St Peter's Square, Vatican City", 41.9022, 12.4573),
        new Location("St Mark's Square, Venice, Italy", 45.4342, 12.3388),
        new Location("Piazza del Duomo, Florence, Italy", 43.7731, 11.2555),
        new Location("Piazza del Duomo, Milan, Italy", 45.4641, 9.1900),
        new Location("Leaning Tower, Pisa, Italy", 43.7230, 10.3966),
        new Location("Lungomare, Naples, Italy", 40.8291, 14.2477),
        new Location("Lake Como waterfront, Como, Italy", 45.8150, 9.0820),
        new Location("Amalfi waterfront, Italy", 40.6340, 14.6027),
        new Location("Vernazza, Cinque Terre, Italy", 44.1350, 9.6837),
        new Location("Seceda ridge, Dolomites, Italy", 46.6002, 11.7254),
        new Location("Gornergrat, Matterhorn view, Switzerland", 45.9839, 7.7848),
        new Location("Chapel Bridge, Lucerne, Switzerland", 47.0517, 8.3073),
        new Location("Lindenhof, Zurich, Switzerland", 47.3730, 8.5401),
        new Location("Jet d'Eau promenade, Geneva, Switzerland", 46.2079, 6.1550),
        new Location("Hohematte, Interlaken, Switzerland", 46.6863, 7.8632),
        new Location("Schonbrunn Palace, Vienna, Austria", 48.1845, 16.3122),
        new Location("Mirabell Gardens, Salzburg, Austria", 47.8054, 13.0411),
        new Location("Hallstatt waterfront, Austria", 47.5622, 13.6493),
        new Location("Golden Roof, Innsbruck, Austria", 47.2685, 11.3933),
        new Location("Marienplatz, Munich, Germany", 48.1372, 11.5756),
        new Location("Brandenburg Gate, Berlin, Germany", 52.5163, 13.3777),
        new Location("Speicherstadt, Hamburg, Germany", 53.5434, 9.9906),
        new Location("Cologne Cathedral square, Germany", 50.9413, 6.9583),
        new Location("Heidelberg Castle terrace, Germany", 49.4106, 8.7154),
        new Location("Neuschwanstein Castle viewpoint, Germany", 47.5576, 10.7498),
        new Location("Dam Square, Amsterdam, Netherlands", 52.3731, 4.8922),
        new Location("Erasmus Bridge, Rotterdam, Netherlands", 51.9090, 4.4872),
        new Location("Grand Place, Brussels, Belgium", 50.8467, 4.3525),
        new Location("Markt, Bruges, Belgium", 51.2089, 3.2242),
        new Location("Old Town Square, Prague, Czechia", 50.0875, 14.4213),
        new Location("Fisherman's Bastion, Budapest, Hungary", 47.5020, 19.0348),
        new Location("Nyhavn, Copenhagen, Denmark", 55.6796, 12.5918),
        new Location("Stortorget, Stockholm, Sweden", 59.3258, 18.0716),
        new Location("Opera House waterfront, Oslo, Norway", 59.9074, 10.7530),
        new Location("Bryggen, Bergen, Norway", 60.3972, 5.3244),
        new Location("Senate Square, Helsinki, Finland", 60.1695, 24.9523),
        new Location("Preseren Square, Ljubljana, Slovenia", 46.0514, 14.5060),
        new Location("Old Town, Dubrovnik, Croatia", 42.6410, 18.1100),
        new Location("Diocletian's Palace, Split, Croatia", 43.5081, 16.4402),
        new Location("Acropolis viewpoint, Athens, Greece", 37.9715, 23.7257),
        new Location("Oia, Santorini, Greece", 36.4618, 25.3753),
        new Location("Shibuya Crossing, Tokyo, Japan", 35.6595, 139.7005),
        new Location("Senso-ji, Tokyo, Japan", 35.7148, 139.7967),
        new Location("Kiyomizu-dera, Kyoto, Japan", 34.9948, 135.7850),
        new Location("Osaka Castle park, Japan", 34.6873, 135.5262),
        new Location("Peace Memorial Park, Hiroshima, Japan", 34.3927, 132.4526),
        new Location("Opera House forecourt, Sydney, Australia", -33.8575, 151.2145),
        new Location("Federation Square, Melbourne, Australia", -37.8179, 144.9690),
        new Location("South Bank, Brisbane, Australia", -27.4783, 153.0231),
        new Location("Kings Park, Perth, Australia", -31.9617, 115.8436),
        new Location("Sky Tower plaza, Auckland, New Zealand", -36.8485, 174.7622)
    );

    private SpawnLocations() {}

    public static Location choose(Optional<Double> savedLat, Optional<Double> savedLon,
                                  boolean randomSpawn, double fixedLat, double fixedLon,
                                  RandomGenerator random) {
        // Saved coordinates always win. Never mix one saved coordinate with a random one.
        if (savedLat.isPresent() || savedLon.isPresent())
            return new Location("Saved location", savedLat.orElse(fixedLat), savedLon.orElse(fixedLon));
        if (!randomSpawn) return new Location("Custom location", fixedLat, fixedLon);
        return ALL.get(random.nextInt(ALL.size()));
    }
}
