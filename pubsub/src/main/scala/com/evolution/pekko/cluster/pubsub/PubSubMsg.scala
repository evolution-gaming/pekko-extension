package com.evolution.pekko.cluster.pubsub

import com.evolution.pekko.serialization.SerializedMsg

final case class PubSubMsg(serializedMsg: SerializedMsg, timestamp: Long)
