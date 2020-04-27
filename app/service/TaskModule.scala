package service

import play.api.inject.SimpleModule
import play.api.inject._

class TaskModule extends SimpleModule(bind[LiveScheduler].toSelf.eagerly())
