package org.cmhh.seasadj

import java.io.File

/**
 * Time series frequency
 *
 * @param frequency integer&ndash;one of 1, 4, or 12
 */
case class Frequency(frequency: Int){
  require(List(1,4,12).contains(frequency))
}

/**
 * Singleton [[Frequency]] objects.
 */
case object Frequency {
  val ANNUAL = Frequency(1)
  val QUARTERLY = Frequency(4)
  val MONTHLY = Frequency(12)
}

/**
 * Time series.
 *
 * @param data data
 * @param start start date of series
 * @param frequency frequency
 */
case class TimeSeries(data: IndexedSeq[Option[Double]], start: Date, frequency: Frequency) extends JSONOutput {
  lazy val size: Int = data.size
  lazy val dates: IndexedSeq[Date] = this.start.seq(data.size)
  lazy val years: IndexedSeq[Int] = dates.map(_.year)
  lazy val periods: IndexedSeq[Int] = dates.map(_.period)

  def map(f: Double => Double): TimeSeries = 
    TimeSeries(
      data.map(x => x match {
        case Some(y) => Some(f(y))
        case _ => None
      }),
      start, frequency
    )

  override def toString: String = {
    val txt: Seq[String] = data.map(x => x match {
      case None => "NA"
      case Some(x) => (math.round(x * 1e8) / 1e8).toString
    })

    val f = frequency.frequency
    val w = math.max(3, txt.map(_.size).max)
    val p = periods.indexOf(1)
    val n = (f - p) % f

    val dates_ = if (n == 0) dates else (dates(0) - n).seq(size + n)
    val txt_ = if (n == 0) txt else ((1 to n).toVector.map(i => "") ++ txt)

    def pad(s: String): String = s"%${w}s".format(s)

    val months = 
      if (f == 4) List("mar", "jun", "sep", "dec")
      else List("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    val hdr = "     " + months.map(pad(_)).mkString(" ")

    val lines = (1 to (dates_.size / f)).map(i => {
      val y = dates_((i - 1) * f).year
      y.toString + " " + txt_.drop((i - 1) * f).take(f).map(pad(_)).mkString(" ")
    }).mkString("\n")

    hdr + "\n" + lines
  }

  def toJSON: String = {
    val d = dates.map(x => s""""$x"""")
    val v = data.map(x => x match {
      case Some(x) => x.toString
      case None => ""
    })
    s"""{"date":[${d.mkString(",")}],"value":[${v.mkString(",")}]}"""
  }

  /**
   * Return specification values.
   */
  def toSpecValue: (SpecNumArray, SpecDate) = 
    (SpecNumArray(data: _*), SpecDate(start))

  /**
   * Subset [[TimeSeries]].
   *
   * @param start start date&ndash;if `None` uses `this.start`
   * @param end end date&ndash;if `None` uses `this.start`
   */
  def window(start: Option[Date], end: Option[Date]): TimeSeries = {
    val s: Date = start match {
      case Some(d) => d
      case _ => dates.head
    }

    val e: Date = end match {
      case Some(d) => d
      case _ => dates.last
    }

    TimeSeries(
      data.zip(dates).filter(x => x._2 >= s & x._2 <= e).map(_._1),
      s,
      frequency
    )
  }

  /**
   * Subset [[TimeSeries]]&ndash;from some date to the end of the series.
   *
   * @param date date
   */
  def from(date: Date): TimeSeries = window(Some(date), None)

  /**
   * Subset [[TimeSeries]]&ndash;from the start of the series to some date.
   *
   * @param date date
   */
  def until(date: Date): TimeSeries = window(None, Some(date))
}

/**
 * Factory methods for [[TimeSeries]].
 */
case object TimeSeries {
  import FileUtils._
  
  def apply[T: Numeric](data: =>IndexedSeq[T], start: Date, frequency: Frequency): TimeSeries = {
    val num = implicitly[Numeric[T]]
    TimeSeries(data.map(x => Some(num.toDouble(x))), start, frequency)
  }

  def fromFile(file: String, skip: Int = 0): TimeSeries = {
    val src = sourceFromPath(file)
    val lines = src.getLines.toVector.drop(skip).map(_.split("\\s+"))
    src.close()
    val n = lines(0).size

    if (n == 3) {
      val year = lines.map(x => x(0).toInt)
      val period = lines.map(x => x(1).toInt)
      val value = lines.map(x => x(2).toDouble)
      val f = period.distinct.size
      if (!List(4, 12).contains(f)) sys.error("Series must be monthly or quarterly.")
      if (f == 4)
        TimeSeries(value, Quarter(year(0), period(0)), Frequency.QUARTERLY)
      else
        TimeSeries(value, Month(year(0), period(0)), Frequency.MONTHLY)
    } else {
      val year = lines.map(x => x(0).take(x(0).size - 2).toInt)
      val period = lines.map(x => x(0).takeRight(2).toInt)
      val value = lines.map(x => x(1).toDouble)
      val f = period.distinct.size
      if (!List(4, 12).contains(f)) sys.error("Series must be monthly or quarterly.")
      if (f == 4)
        TimeSeries(value, Quarter(year(0), period(0)), Frequency.QUARTERLY)
      else
        TimeSeries(value, Month(year(0), period(0)), Frequency.MONTHLY)
    }
  }

  def fromFile(file: File, skip: Int): TimeSeries = fromFile(file.getAbsolutePath, skip)
}