package cc.acc.shared

interface FleetManager {
    fun getModels(): List<AIModel>
    fun addModel(model: AIModel)
    fun removeModel(name: String)
    fun sync()
}

class CommonFleetManager : FleetManager {
    private val models = mutableListOf<AIModel>()

    override fun getModels(): List<AIModel> = models.toList()

    override fun addModel(model: AIModel) {
        models.add(model)
    }

    override fun removeModel(name: String) {
        models.removeAll { it.name == name }
    }

    override fun sync() {
        // Implementation for syncing with .conf files will go here
        // (Expected to use expect/actual or a pure Kotlin parser)
    }
}
