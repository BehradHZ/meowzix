package dev.behradhz.meowzix.domain.recommendation

import kotlin.math.sqrt

/** Small dense SPD solver. Factors once per batch, never forms an inverse. */
object PositiveDefiniteSolver {
    fun cholesky(matrix: DoubleArray, size: Int): DoubleArray {
        require(size > 0 && matrix.size == size * size)
        require(matrix.all(Double::isFinite))
        for (i in 0 until size) for (j in 0 until i) {
            require(kotlin.math.abs(matrix[i * size + j] - matrix[j * size + i]) < 1e-8) { "Non-symmetric matrix" }
        }
        val l = DoubleArray(matrix.size)
        for (i in 0 until size) for (j in 0..i) {
            var value = matrix[i * size + j]
            for (k in 0 until j) value -= l[i * size + k] * l[j * size + k]
            if (i == j) {
                require(value > 0.0 && value.isFinite()) { "Matrix is not positive definite" }
                l[i * size + j] = sqrt(value)
            } else l[i * size + j] = value / l[j * size + j]
        }
        return l
    }

    fun solve(factor: DoubleArray, rhs: DoubleArray): DoubleArray {
        val n = rhs.size
        require(factor.size == n * n && rhs.all(Double::isFinite))
        val result = rhs.copyOf()
        for (i in 0 until n) {
            for (j in 0 until i) result[i] -= factor[i * n + j] * result[j]
            result[i] /= factor[i * n + i]
        }
        for (i in n - 1 downTo 0) {
            for (j in i + 1 until n) result[i] -= factor[j * n + i] * result[j]
            result[i] /= factor[i * n + i]
        }
        require(result.all(Double::isFinite))
        return result
    }
}

/** One parameter matrix for the entire library; no per-song parameters. */
class SharedLinUcb(
    val dimension: Int = PersonalizationFeatureVectorizer.FEATURE_COUNT,
    val regularization: Double = RecommendationConfig.REGULARIZATION,
    matrix: DoubleArray? = null,
    rewardVector: DoubleArray? = null,
) {
    val a: DoubleArray = matrix?.copyOf() ?: DoubleArray(dimension * dimension).also { m ->
        for (i in 0 until dimension) m[i * dimension + i] = regularization
    }
    val b: DoubleArray = rewardVector?.copyOf() ?: DoubleArray(dimension)
    private var factor: DoubleArray? = null
    private var theta: DoubleArray? = null

    init {
        require(dimension > 0 && regularization.isFinite() && regularization > 0.0)
        require(a.size == dimension * dimension && b.size == dimension && b.all(Double::isFinite))
    }

    fun copy(): SharedLinUcb = SharedLinUcb(dimension, regularization, a, b)

    fun update(features: DoubleArray, reward: Double, weight: Double = 1.0) {
        require(features.size == dimension && features.all(Double::isFinite))
        require(reward.isFinite() && reward in -1.0..1.0 && weight.isFinite() && weight > 0.0 && weight <= 10.0)
        for (i in 0 until dimension) {
            b[i] += weight * reward * features[i]
            for (j in 0..i) {
                val increment = weight * features[i] * features[j]
                a[i * dimension + j] += increment
                if (i != j) a[j * dimension + i] += increment
            }
        }
        factor = null
        theta = null
    }

    private fun factor(): DoubleArray = factor ?: run {
        // A = lambda I + sum(w xx^T) is SPD. Tiny round-off jitter is bounded and local.
        val result = try { PositiveDefiniteSolver.cholesky(a, dimension) } catch (failure: IllegalArgumentException) {
            PositiveDefiniteSolver.cholesky(a.copyOf().also { m ->
                for (i in 0 until dimension) m[i * dimension + i] += 1e-8
            }, dimension)
        }
        factor = result
        result
    }

    fun coefficients(): DoubleArray = (theta ?: PositiveDefiniteSolver.solve(factor(), b).also { theta = it }).copyOf()

    fun predict(features: DoubleArray): Pair<Double, Double> {
        require(features.size == dimension && features.all(Double::isFinite))
        val weights = theta ?: PositiveDefiniteSolver.solve(factor(), b).also { theta = it }
        val solved = PositiveDefiniteSolver.solve(factor(), features)
        var expectation = 0.0
        var variance = 0.0
        for (i in features.indices) {
            expectation += weights[i] * features[i]
            variance += features[i] * solved[i]
        }
        return expectation.coerceIn(-1.0, 1.0) to sqrt(variance.coerceAtLeast(0.0))
    }
}
