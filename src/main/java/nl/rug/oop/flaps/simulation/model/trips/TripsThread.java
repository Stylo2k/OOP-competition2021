package nl.rug.oop.flaps.simulation.model.trips;

import lombok.SneakyThrows;
import nl.rug.oop.flaps.simulation.controller.SpeedRateUp;

public class TripsThread extends Thread{

    private final Trip newTrip;

    /**
     * creates a new thread to process the trip
     * */
    public TripsThread(Trip newTrip) {
        this.newTrip = newTrip;
    }

    @SneakyThrows
    @Override
    public void run() {
        while (!newTrip.isReachedDestination()) {
            newTrip.cruise();
            long newRate = 100;
            for (long i = (long) ((SpeedRateUp.getRATE()/0.5-2)); i > 0; i--) {
                newRate = newRate / 2;
            }
            Thread.sleep(newRate);
        }
    }

}
