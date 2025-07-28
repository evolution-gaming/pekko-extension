package com.evolution.pekko.cluster.pubsub

import com.evolution.serialization.SerializedMsg

final case class PubSubMsg(serializedMsg: SerializedMsg, timestamp: Long)
