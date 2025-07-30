package com.evolution.pekko.effect.persistence

package object api {

  type SeqNr = Long

  object SeqNr {

    val Min: SeqNr = 0L

    val Max: SeqNr = Long.MaxValue
  }

  type Timestamp = Long

}
