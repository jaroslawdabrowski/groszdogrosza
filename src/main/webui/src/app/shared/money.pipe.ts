import { Pipe, PipeTransform } from '@angular/core';

const whole = new Intl.NumberFormat('pl-PL', { maximumFractionDigits: 0 });
const withGrosze = new Intl.NumberFormat('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** A złoty amount in Polish notation, without the currency: `308`, `12,50`, `25 000`. Whole
 *  amounts show no decimals (most collections are whole złoty); anything with grosze always
 *  shows both digits, so "12,5" never appears. The template adds "zł" itself, so the amount
 *  and the currency can be styled differently. */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value == null) {
      return '';
    }
    return Number.isInteger(value) ? whole.format(value) : withGrosze.format(value);
  }
}
