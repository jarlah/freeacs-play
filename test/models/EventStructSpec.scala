package models
import org.scalatestplus.play.PlaySpec

class EventStructSpec extends PlaySpec {

  "An EventStruct" should {
    "be able to parse events" in new CwmpContext() {
      val xml    = mkIN()
      val events = EventStruct.fromNode(xml)
      events.length mustBe 3

      events.head.eventCode mustBe "0 BOOTSTRAP"
      events.head.commandKey mustBe Some("TR069_FakeManufacturer_HOMEGATEWAY")
      events.head.factoryReset mustBe true

      events(1).eventCode mustBe "1 BOOT"
      events(1).commandKey mustBe None
      events(1).booted mustBe true

      events.last.eventCode mustBe "4 VALUE CHANGE"
      events.last.commandKey mustBe None
      events.last.valueChange mustBe true
    }
  }
}
