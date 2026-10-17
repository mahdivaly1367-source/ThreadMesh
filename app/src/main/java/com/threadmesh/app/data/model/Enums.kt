package com.threadmesh.app.data.model

enum class EventType {
    ROOT_THREAD,
    REPLY,
    STANCE_CHANGED
}

enum class PublishScope(val labelFa: String, val isTransmissibleInPhase1: Boolean) {
    LOCAL_ONLY("فقط محلی (LOCAL_ONLY)", false),
    MESH_PUBLIC("عمومی در مش (MESH_PUBLIC)", false),
    GLOBAL_PUBLIC("عمومی سراسری (GLOBAL_PUBLIC)", false);

    companion object {
        const val PHASE1_NOTICE = "انتقال در این نسخه هنوز فعال نیست"
    }
}
