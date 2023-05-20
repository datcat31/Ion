package net.horizonsend.ion.server.features.dl4j

import org.deeplearning4j.datasets.iterator.utilty.ListDataSetIterator
import org.deeplearning4j.nn.conf.NeuralNetConfiguration
import org.deeplearning4j.nn.conf.layers.DenseLayer
import org.deeplearning4j.nn.conf.layers.OutputLayer
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.learning.config.Nesterovs
import org.nd4j.linalg.lossfunctions.LossFunctions
import kotlin.random.Random


class MakeModel {

	final val seed = 12345
	final val nEpochs = 1000000
	final val nSamples = 1000000
	final val batchSize = 1
	final val learningRate = 0.01
	private val rng: Random = Random(seed)

	fun make(){
		val iterator: DataSetIterator = compute(batchSize, rng)

		val numInput = 6
		val numOutputs = 8
		val nHidden = 300
		val net = MultiLayerNetwork(
			NeuralNetConfiguration.Builder()
				.seed(seed.toLong())
				.weightInit(WeightInit.XAVIER)
				.updater(Nesterovs(learningRate, 0.9))
				.list()
				.layer(
					0, DenseLayer.Builder().nIn(numInput).nOut(nHidden)
						.activation(Activation.RELU) //Change this to RELU and you will see the net learns very well very quickly
						.build()
				)
				.layer(
					1, DenseLayer.Builder()
						.activation(Activation.RELU)
						.nIn(nHidden).nOut(nHidden).build()
				)
				.layer(
					2, DenseLayer.Builder()
						.activation(Activation.RELU)
						.nIn(nHidden).nOut(nHidden).build()
				)
				.layer(
					3, DenseLayer.Builder()
						.activation(Activation.RELU)
						.nIn(nHidden).nOut(nHidden).build()
				)
				.layer(
					4, OutputLayer.Builder(LossFunctions.LossFunction.MSE)
						.activation(Activation.IDENTITY)
						.nIn(nHidden).nOut(numOutputs).build()
				)
				.build()
		)
		net.init()
		net.setListeners(ScoreIterationListener(1))


		//Train the network on the full data set, and evaluate in periodically


		//Train the network on the full data set, and evaluate in periodically
		for (i in 0 until nEpochs) {
			iterator.reset()
			net.fit(iterator)
		}
		// Test the addition of 2 numbers (Try different numbers here)
		// Test the addition of 2 numbers (Try different numbers here)
		val input = Nd4j.create(doubleArrayOf(0.111111, 0.3333333333333), 1, 2)
		val out = net.output(input, false)
		println(out)
	}
	private fun compute(batchSize: Int, rand: Random): DataSetIterator {
		val dx = DoubleArray(nSamples)
		val dy = DoubleArray(nSamples)
		val dz = DoubleArray(nSamples)
		val world = DoubleArray(nSamples)
		val AIShipType = DoubleArray(nSamples)
		val weaponRange = DoubleArray(nSamples)
		var arrayOut = IntArray(10)
		val mx = IntArray(nSamples)
		val my = IntArray(nSamples)
		val mz = IntArray(nSamples)
		val mb = IntArray(nSamples)
		val sb = IntArray(nSamples)
		val sx = IntArray(nSamples)
		val sy = IntArray(nSamples)
		val sz = IntArray(nSamples)
		for (i in 0 until nSamples) {
			dx[i] = rand.nextDouble(1000.0)
			dy[i] = rand.nextDouble(1000.0)
			dz[i] = rand.nextDouble(1000.0)
			world[i] = rand.nextInt(11).toDouble()
			AIShipType[i] = rand.nextInt(16).toDouble()
			weaponRange[i] = rand.nextInt(100,500).toDouble()

			arrayOut = modelFun(dx[i],dy[i],dz[i],world[i],AIShipType[i],weaponRange[i])
			mx[i] = arrayOut[1]
			my[i] = arrayOut[2]
		}
		val dxArray = Nd4j.create(dx, nSamples.toLong(), 1)
		val dyArray = Nd4j.create(dy, nSamples.toLong(), 1)
		val dzArray = Nd4j.create(dz, nSamples.toLong(), 1)
		val worldArray = Nd4j.create(world, nSamples.toLong(), 1)
		val shipTypeArray = Nd4j.create(AIShipType, nSamples.toLong(), 1)
		val weaponRangeArray = Nd4j.create(weaponRange, nSamples.toLong(), 1)
		val inputNDArray = Nd4j.hstack(dxArray, dyArray, dzArray, worldArray, shipTypeArray, weaponRangeArray)
		val mxArray = Nd4j.create(mx,mx)
		val myArray = Nd4j.create(my,my)
		val mzArray = Nd4j.create(mz,mz)
		val mbArray = Nd4j.create(mb,mb)
		val sxArray = Nd4j.create(sx,sx)
		val syArray = Nd4j.create(sy,sy)
		val szArray = Nd4j.create(sz,sz)
		val sbArray = Nd4j.create(sb,sb)

		val outPut = Nd4j.hstack(mxArray,myArray,mzArray,mbArray,sxArray,syArray,szArray,sbArray)
		val dataSet = DataSet(inputNDArray, outPut)
		val listDs = dataSet.asList()
		listDs.shuffle(rng)
		return ListDataSetIterator(listDs, batchSize)
	}

	fun modelFun(x :Double, y :Double, z :Double, w :Double, st :Double, wR :Double): IntArray {
		val valout: IntArray = IntArray(10)
		valout[1] = 2
		return valout
	}
}
