package it.units.erallab.hmsrobots.core.controllers.snn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.units.erallab.hmsrobots.core.controllers.Resettable;
import it.units.erallab.hmsrobots.core.controllers.TimedRealFunction;
import it.units.erallab.hmsrobots.core.controllers.snn.converters.stv.MovingAverageSpikeTrainToValueConverter;
import it.units.erallab.hmsrobots.core.controllers.snn.converters.stv.SpikeTrainToValueConverter;
import it.units.erallab.hmsrobots.core.controllers.snn.converters.vts.UniformWithMemoryValueToSpikeTrainConverter;
import it.units.erallab.hmsrobots.core.controllers.snn.converters.vts.ValueToSpikeTrainConverter;
import it.units.erallab.hmsrobots.util.Parametrized;
import it.units.erallab.hmsrobots.util.SerializationUtils;

import java.io.Serializable;
import java.util.List;
import java.util.SortedSet;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

public class MultilayerSpikingNetworkWithConverters implements TimedRealFunction, Parametrized, Serializable, Resettable {

  @JsonProperty
  private final MultilayerSpikingNetwork multilayerSpikingNetwork;
  @JsonProperty
  private final ValueToSpikeTrainConverter[] valueToSpikeTrainConverters;
  @JsonProperty
  private final SpikeTrainToValueConverter[] spikeTrainToValueConverters;

  private double previousApplicationTime = 0d;

  @SuppressWarnings("unchecked")
  @JsonCreator
  public MultilayerSpikingNetworkWithConverters(
      @JsonProperty("multilayerSpikingNetwork") MultilayerSpikingNetwork multilayerSpikingNetwork,
      @JsonProperty("valueToSpikeTrainConverters") ValueToSpikeTrainConverter[] valueToSpikeTrainConverter,
      @JsonProperty("spikeTrainToValueConverters") SpikeTrainToValueConverter[] spikeTrainToValueConverter
  ) {
    this.multilayerSpikingNetwork = multilayerSpikingNetwork;
    this.valueToSpikeTrainConverters = valueToSpikeTrainConverter;
    this.spikeTrainToValueConverters = spikeTrainToValueConverter;
    reset();
  }

  public MultilayerSpikingNetworkWithConverters(MultilayerSpikingNetwork multilayerSpikingNetwork) {
    this(multilayerSpikingNetwork,
        createInputConverters(multilayerSpikingNetwork.getInputDimension(), new UniformWithMemoryValueToSpikeTrainConverter()),
        createOutputConverters(multilayerSpikingNetwork.getOutputDimension(), new MovingAverageSpikeTrainToValueConverter()));
  }

  public MultilayerSpikingNetworkWithConverters(SpikingFunction[][] neurons, double[][][] weights, ValueToSpikeTrainConverter valueToSpikeTrainConverter, SpikeTrainToValueConverter spikeTrainToValueConverter) {
    this(new MultilayerSpikingNetwork(neurons, weights),
        createInputConverters(neurons[0].length, valueToSpikeTrainConverter),
        createOutputConverters(neurons[neurons.length - 1].length, spikeTrainToValueConverter));
  }

  public MultilayerSpikingNetworkWithConverters(SpikingFunction[][] neurons, double[][][] weights, ValueToSpikeTrainConverter[] valueToSpikeTrainConverters, SpikeTrainToValueConverter[] spikeTrainToValueConverters) {
    this(new MultilayerSpikingNetwork(neurons, weights), valueToSpikeTrainConverters, spikeTrainToValueConverters);
  }

  public MultilayerSpikingNetworkWithConverters(SpikingFunction[][] neurons, double[][][] weights) {
    this(new MultilayerSpikingNetwork(neurons, weights),
        createInputConverters(neurons[0].length, new UniformWithMemoryValueToSpikeTrainConverter()),
        createOutputConverters(neurons[neurons.length - 1].length, new MovingAverageSpikeTrainToValueConverter()));
  }

  public MultilayerSpikingNetworkWithConverters(int nOfInput, int[] innerNeurons, int nOfOutput, double[] weights, SpikingFunction spikingFunction) {
    this(new MultilayerSpikingNetwork(nOfInput, innerNeurons, nOfOutput, weights, spikingFunction));
  }

  public MultilayerSpikingNetworkWithConverters(int nOfInput, int[] innerNeurons, int nOfOutput, double[] weights, SpikingFunction spikingFunction, ValueToSpikeTrainConverter valueToSpikeTrainConverter, SpikeTrainToValueConverter spikeTrainToValueConverter) {
    this(new MultilayerSpikingNetwork(nOfInput, innerNeurons, nOfOutput, weights, spikingFunction), createInputConverters(nOfInput, valueToSpikeTrainConverter), createOutputConverters(nOfOutput, spikeTrainToValueConverter));
  }

  public MultilayerSpikingNetworkWithConverters(int nOfInput, int[] innerNeurons, int nOfOutput, SpikingFunction spikingFunction) {
    this(new MultilayerSpikingNetwork(nOfInput, innerNeurons, nOfOutput, spikingFunction));
  }

  public MultilayerSpikingNetworkWithConverters(int nOfInput, int[] innerNeurons, int nOfOutput, SpikingFunction spikingFunction, ValueToSpikeTrainConverter valueToSpikeTrainConverter, SpikeTrainToValueConverter spikeTrainToValueConverter) {
    this(new MultilayerSpikingNetwork(nOfInput, innerNeurons, nOfOutput, spikingFunction), createInputConverters(nOfInput, valueToSpikeTrainConverter), createOutputConverters(nOfOutput, spikeTrainToValueConverter));
  }

  public MultilayerSpikingNetworkWithConverters(int nOfInput, int[] innerNeurons, int nOfOutput, double[] weights, BiFunction<Integer, Integer, SpikingFunction> neuronBuilder, ValueToSpikeTrainConverter valueToSpikeTrainConverter, SpikeTrainToValueConverter spikeTrainToValueConverter) {
    this(new MultilayerSpikingNetwork(nOfInput, innerNeurons, nOfOutput, weights, neuronBuilder), createInputConverters(nOfInput, valueToSpikeTrainConverter), createOutputConverters(nOfOutput, spikeTrainToValueConverter));
  }

  public MultilayerSpikingNetworkWithConverters(int nOfInput, int[] innerNeurons, int nOfOutput, BiFunction<Integer, Integer, SpikingFunction> neuronBuilder) {
    this(new MultilayerSpikingNetwork(nOfInput, innerNeurons, nOfOutput, neuronBuilder));
  }

  public MultilayerSpikingNetworkWithConverters(int nOfInput, int[] innerNeurons, int nOfOutput, BiFunction<Integer, Integer, SpikingFunction> neuronBuilder, ValueToSpikeTrainConverter valueToSpikeTrainConverter, SpikeTrainToValueConverter spikeTrainToValueConverter) {
    this(new MultilayerSpikingNetwork(nOfInput, innerNeurons, nOfOutput, neuronBuilder), createInputConverters(nOfInput, valueToSpikeTrainConverter), createOutputConverters(nOfOutput, spikeTrainToValueConverter));
  }

  public MultilayerSpikingNetworkWithConverters(SpikingFunction[][] neurons, double[] weights) {
    this(new MultilayerSpikingNetwork(neurons, weights));
  }

  public MultilayerSpikingNetworkWithConverters(SpikingFunction[][] neurons, double[] weights, ValueToSpikeTrainConverter valueToSpikeTrainConverter, SpikeTrainToValueConverter spikeTrainToValueConverter) {
    this(new MultilayerSpikingNetwork(neurons, weights), createInputConverters(neurons[0].length, valueToSpikeTrainConverter),
        createOutputConverters(neurons[neurons.length - 1].length, spikeTrainToValueConverter));
  }

  @SuppressWarnings("unchecked")
  @Override
  public double[] apply(double t, double[] input) {
    double deltaT = t - previousApplicationTime;
    SortedSet<Double>[] inputSpikes = new SortedSet[input.length];
    IntStream.range(0, input.length).forEach(i ->
        inputSpikes[i] = valueToSpikeTrainConverters[i].convert(input[i], deltaT, t));
    SortedSet<Double>[] outputSpikes = multilayerSpikingNetwork.apply(t, inputSpikes);
    previousApplicationTime = t;
    double[] output = new double[outputSpikes.length];
    IntStream.range(0, outputSpikes.length).forEach(i ->
        output[i] = spikeTrainToValueConverters[i].convert(outputSpikes[i], deltaT));
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

  private static ValueToSpikeTrainConverter[] createInputConverters(int nOfInputs, ValueToSpikeTrainConverter valueToSpikeTrainConverter) {
    ValueToSpikeTrainConverter[] valueToSpikeTrainConverters = new ValueToSpikeTrainConverter[nOfInputs];
    IntStream.range(0, nOfInputs).forEach(i -> {
      valueToSpikeTrainConverters[i] = SerializationUtils.clone(valueToSpikeTrainConverter);
      valueToSpikeTrainConverters[i].reset();
    });
    return valueToSpikeTrainConverters;
  }

  private static SpikeTrainToValueConverter[] createOutputConverters(int nOfOutputs, SpikeTrainToValueConverter spikeTrainToValueConverter) {
    SpikeTrainToValueConverter[] spikeTrainToValueConverters = new SpikeTrainToValueConverter[nOfOutputs];
    IntStream.range(0, nOfOutputs).forEach(i -> {
      spikeTrainToValueConverters[i] = SerializationUtils.clone(spikeTrainToValueConverter);
      spikeTrainToValueConverters[i].reset();
    });
    return spikeTrainToValueConverters;
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
    previousApplicationTime = 0d;
    IntStream.range(0, spikeTrainToValueConverters.length).forEach(i ->
        spikeTrainToValueConverters[i].reset());
    IntStream.range(0, valueToSpikeTrainConverters.length).forEach(i ->
        valueToSpikeTrainConverters[i].reset());
  }

}