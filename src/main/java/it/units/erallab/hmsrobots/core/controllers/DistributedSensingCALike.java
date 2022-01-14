/*
 * Copyright (C) 2021 Eric Medvet <eric.medvet@gmail.com> (as Eric Medvet <eric.medvet@gmail.com>)
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.units.erallab.hmsrobots.core.controllers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.units.erallab.hmsrobots.core.objects.SensingVoxel;
import it.units.erallab.hmsrobots.util.Grid;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Objects;

/**
 * @author eric
 */
public class DistributedSensingCALike extends AbstractController<SensingVoxel> {

  private enum Dir {

    N(0, -1),
    E(1, 0),
    S(0, 1),
    W(-1, 0);

    private final int dx;
    private final int dy;

    Dir(int dx, int dy) {
      this.dx = dx;
      this.dy = dy;
    }

    private static Dir adjacent(Dir dir) {
      return switch (dir) {
        case N -> Dir.S;
        case E -> Dir.W;
        case S -> Dir.N;
        case W -> Dir.E;
      };
    }
  }

  private static class FunctionWrapper implements TimedRealFunction {
    @JsonProperty
    private final TimedRealFunction inner;

    @JsonCreator
    public FunctionWrapper(@JsonProperty("inner") TimedRealFunction inner) {
      this.inner = inner;
    }

    @Override
    public double[] apply(double t, double[] in) {
      return inner.apply(t, in);
    }

    @Override
    public int getInputDimension() {
      return inner.getInputDimension();
    }

    @Override
    public int getOutputDimension() {
      return inner.getOutputDimension();
    }
  }

  @JsonProperty
  private final int stateSize;
  @JsonProperty
  private final Grid<Integer> nOfInputGrid;
  @JsonProperty
  private final Grid<Integer> nOfOutputGrid;
  @JsonProperty
  private final Grid<TimedRealFunction> functions;

  private final Grid<double[]> lastSignalsGrid;
  private final Grid<double[]> currentSignalsGrid;

  public static int nOfInputs(SensingVoxel voxel, int stateSize) {
    return stateSize * Dir.values().length + voxel.getSensors().stream().mapToInt(s -> s.getDomains().length).sum();
  }

  public static int nOfOutputs(SensingVoxel voxel, int stateSize) {
    return 1 + stateSize;
  }

  @JsonCreator
  public DistributedSensingCALike(
      @JsonProperty("stateSize") int stateSize,
      @JsonProperty("nOfInputGrid") Grid<Integer> nOfInputGrid,
      @JsonProperty("nOfOutputGrid") Grid<Integer> nOfOutputGrid,
      @JsonProperty("functions") Grid<TimedRealFunction> functions
  ) {
    this.stateSize = stateSize;
    this.nOfInputGrid = nOfInputGrid;
    this.nOfOutputGrid = nOfOutputGrid;
    this.functions = functions;
    lastSignalsGrid = Grid.create(functions, f -> new double[stateSize]);
    currentSignalsGrid = Grid.create(functions, f -> new double[stateSize]);
    reset();
  }

  public DistributedSensingCALike(Grid<? extends SensingVoxel> voxels, int stateSize) {
    this(
        stateSize,
        Grid.create(voxels, v -> (v == null) ? 0 : nOfInputs(v, stateSize)),
        Grid.create(voxels, v -> (v == null) ? 0 : nOfOutputs(v, stateSize)),
        Grid.create(
            voxels.getW(),
            voxels.getH(),
            (x, y) -> voxels.get(x, y) == null ? null : new FunctionWrapper(RealFunction.build(
                (double[] in) -> new double[1 + stateSize],
                nOfInputs(voxels.get(x, y), stateSize),
                nOfOutputs(voxels.get(x, y), stateSize))
            )
        )
    );
  }

  public Grid<TimedRealFunction> getFunctions() {
    return functions;
  }

  @Override
  public void reset() {
    for (int x = 0; x < lastSignalsGrid.getW(); x++) {
      for (int y = 0; y < lastSignalsGrid.getH(); y++) {
        lastSignalsGrid.set(x, y, new double[stateSize]);
      }
    }
    for (int x = 0; x < currentSignalsGrid.getW(); x++) {
      for (int y = 0; y < currentSignalsGrid.getH(); y++) {
        currentSignalsGrid.set(x, y, new double[stateSize]);
      }
    }
    functions.values().stream().filter(Objects::nonNull).forEach(f -> {
      if (f instanceof Resettable) {
        ((Resettable) f).reset();
      }
    });
  }


  @Override
  public Grid<Double> computeControlSignals(double t, Grid<? extends SensingVoxel> voxels) {
    Grid<Double> controlSignals = Grid.create(voxels.getW(), voxels.getH());
    for (Grid.Entry<? extends SensingVoxel> entry : voxels) {
      if (entry.getValue() == null) {
        continue;
      }
      //get inputs
      double[] signals = getLastSignals(entry.getX(), entry.getY());
      double[] inputs = ArrayUtils.addAll(entry.getValue().getSensorReadings(), signals);
      //compute outputs
      TimedRealFunction function = functions.get(entry.getX(), entry.getY());
      double[] outputs = function != null ? function.apply(t, inputs) : new double[1 + this.stateSize];
      //save outputs
      controlSignals.set(entry.getX(), entry.getY(), outputs[0]);
      System.arraycopy(outputs, 1, currentSignalsGrid.get(entry.getX(), entry.getY()), 0, this.stateSize);
    }
    for (Grid.Entry<? extends SensingVoxel> entry : voxels) {
      if (entry.getValue() == null) {
        continue;
      }
      int x = entry.getX();
      int y = entry.getY();
      System.arraycopy(currentSignalsGrid.get(x, y), 0, lastSignalsGrid.get(x, y), 0, stateSize);
    }
    return controlSignals;
  }

  public int nOfInputs(int x, int y) {
    return nOfInputGrid.get(x, y);
  }

  public int nOfOutputs(int x, int y) {
    return nOfOutputGrid.get(x, y);
  }

  private double[] getLastSignals(int x, int y) {
    double[] values = new double[stateSize * Dir.values().length];
    if (stateSize <= 0) {
      return values;
    }
    int c = 0;
    for (Dir dir : Dir.values()) {
      int adjacentX = x + dir.dx;
      int adjacentY = y + dir.dy;
      double[] lastSignals = lastSignalsGrid.get(adjacentX, adjacentY);
      if (lastSignals != null) {
        System.arraycopy(lastSignals, 0, values, c, stateSize);
      }
      c = c + stateSize;
    }
    return values;
  }

  @Override
  public String toString() {
    return "DistributedSensing{" +
        "signals=" + stateSize +
        ", functions=" + functions +
        '}';
  }
}