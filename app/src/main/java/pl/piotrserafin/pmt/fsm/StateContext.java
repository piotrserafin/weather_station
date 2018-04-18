package pl.piotrserafin.pmt.fsm;

public class StateContext {
    private State state;

    public StateContext(final State state) {
        this.state = state;
    }

    public State getState() {
        return state;
    }

    public void setState(final State state) {
        this.state = state;
    }

    public void takeAction() {
        state.takeAction(this);
    }
}
