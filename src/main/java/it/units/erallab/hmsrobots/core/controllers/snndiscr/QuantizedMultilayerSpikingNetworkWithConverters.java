package it.units.erallab.hmsrobots.core.controllers.snndiscr;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.units.erallab.hmsrobots.core.controllers.Resettable;
import it.units.erallab.hmsrobots.core.controllers.TimedRealFunction;
import it.units.erallab.hmsrobots.core.controllers.snndiscr.converters.stv.QuantizedMovingAverageSpikeTrainToValueConverter;
import it.units.erallab.hmsrobots.core.controllers.snndiscr.converters.stv.QuantizedSpikeTrainToValueConverter;
import it.units.erallab.hmsrobots.core.controllers.snndiscr.converters.vts.QuantizedUniformWithMemoryValueToSpikeTrainConverter;
import it.units.erallab.hmsrobots.core.controllers.snndiscr.converters.vts.QuantizedValueToSpikeTrainConverter;
import it.units.erallab.hmsrobots.util.Parametrized;
import it.units.erallab.hmsrobots.util.SerializationUtils;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

public class QuantizedMultilayerSpikingNetworkWithConverters implements TimedRealFunction, Parametrized, Resettable {

  @JsonProperty
  private final QuantizedMultilayerSpikingNetwork multilayerSpikingNetwork;
  @JsonProperty
  private final QuantizedValueToSpikeTrainConverter[] quantizedValueToSpikeTrainConverters;
  @JsonProperty
  private final QuantizedSpikeTrainToValueConverter[] quantizedSpikeTrainToValueConverters;

  private double previousApplicationTime = 0d;

  @JsonCreator
  public QuantizedMultilayerSpikingNetworkWithConverters(
      @JsonProperty("multilayerSpikingNetwork") QuantizedMultilayerSpikingNetwork multilayerSpikingNetwork,
      @JsonProperty("valueToSpikeTrainConverters") QuantizedValueToSpikeTrainConverter[] quantizedValueToSpikeTrainConverter,
      @JsonProperty("spikeTrainToValueConverters") QuantizedSpikeTrainToValueConverter[] quantizedSpikeTrainToValueConverter
  ) {
    this.multilayerSpikingNetwork = multilayerSpikingNetwork;
    this.quantizedValueToSpikeTrainConverters = quantizedValueToSpikeTrainConverter;
    this.quantizedSpikeTrainToValueConverters = quantizedSpikeTrainToValueConverter;
    reset();
  }

  public QuantizedMultilayerSpikingNetworkWithConverters(QuantizedMultilayerSpikingNetwork multilayerSpikingNetwork) {
    this(multilayerSpikingNetwork,
        createInputConverters(multilayerSpikingNetwork.getInputDimension(), new QuantizedUniformWithMemoryValueToSpikeTrainConverter()),
        createOutputConverters(multilayerSpikingNetwork.getOutputDimension(), new QuantizedMovingAverageSpikeTrainToValueConverter()));
  }

  public QuantizedMultilayerSpikingNetworkWithConverters(QuantizedMultilayerSpikingNetwork multilayerSpikingNetwork, QuantizedValueToSpikeTrainConverter quantizedValueToSpikeTrainConverter, QuantizedSpikeTrainToValueConverter quantizedSpikeTrainToValueConverter) {
    this(multilayerSpikingNetwork,
        createInputConverters(multilayerSpikingNetwork.getInputDimension(), quantizedValueToSpikeTrainConverter),
        createOutputConverters(multilayerSpikingNetwork.getOutputDimension(), quantizedSpikeTrainToValueConverter));
  }

  @Override
  public double[] apply(double t, double[] input) {
    double deltaT = t - previousApplicationTime;
    int[][] inputSpikes = new int[input.length][];
    IntStream.range(0, input.length).forEach(i ->
        inputSpikes[i] = quantizedValueToSpikeTrainConverters[i].convert(input[i], deltaT, t));
    int[][] outputSpikes = multilayerSpikingNetwork.apply(t, inputSpikes);
    previousApplicationTime = t;
    double[] output = new double[outputSpikes.length];
    IntStream.range(0, outputSpikes.length).forEach(i ->
        output[i] = quantizedSpikeTrainToValueConverters[i].convert(outputSpikes[i], deltaT));
    return output;
  }

  @Override
  public int getInputDimension() {
    return multilayerSpikingNetwork.getInputDimension();
  }

  @Override
  public int getOutputDimension() {
    return multilayerSpikingNetwork.getOutputDimension();
  }

  public QuantizedMultilayerSpikingNetwork getMultilayerSpikingNetwork() {
    return multilayerSpikingNetwork;
  }

  protected static QuantizedValueToSpikeTrainConverter[] createInputConverters(int nOfInputs, QuantizedValueToSpikeTrainConverter quantizedValueToSpikeTrainConverter) {
    QuantizedValueToSpikeTrainConverter[] quantizedValueToSpikeTrainConverters = new QuantizedValueToSpikeTrainConverter[nOfInputs];
    IntStream.range(0, nOfInputs).forEach(i -> {
      quantizedValueToSpikeTrainConverters[i] = SerializationUtils.clone(quantizedValueToSpikeTrainConverter);
      quantizedValueToSpikeTrainConverters[i].reset();
    });
    return quantizedValueToSpikeTrainConverters;
  }

  protected static QuantizedSpikeTrainToValueConverter[] createOutputConverters(int nOfOutputs, QuantizedSpikeTrainToValueConverter quantizedSpikeTrainToValueConverter) {
    QuantizedSpikeTrainToValueConverter[] quantizedSpikeTrainToValueConverters = new QuantizedSpikeTrainToValueConverter[nOfOutputs];
    IntStream.range(0, nOfOutputs).forEach(i -> {
      quantizedSpikeTrainToValueConverters[i] = SerializationUtils.clone(quantizedSpikeTrainToValueConverter);
      quantizedSpikeTrainToValueConverters[i].reset();
    });
    return quantizedSpikeTrainToValueConverters;
  }

  @Override
  public double[] getParams() {
    return multilayerSpikingNetwork.getParams();
  }

  @Override
  public void setParams(double[] params) {
    multilayerSpikingNetwork.setParams(params);
    reset();
  }

  public void setPlotMode(boolean plotMode) {
    multilayerSpikingNetwork.setPlotMode(plotMode);
  }

  public void setSpikesTracker(boolean spikesTracker) {
    multilayerSpikingNetwork.setSpikesTracker(spikesTracker);
  }

  public List<Double>[][] getSpikes() {
    return multilayerSpikingNetwork.getSpikes();
  }

  @Override
  public void reset() {
    multilayerSpikingNetwork.reset();
    previousApplicationTime = 0d;
    IntStream.range(0, quantizedSpikeTrainToValueConverters.length).forEach(i ->
        quantizedSpikeTrainToValueConverters[i].reset());
    IntStream.range(0, quantizedValueToSpikeTrainConverters.length).forEach(i ->
        quantizedValueToSpikeTrainConverters[i].reset());
  }

}