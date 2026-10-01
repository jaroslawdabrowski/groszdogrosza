/** Shared presentation for ledger entries (student view + global ledger). The backend ships
 *  an event type + params; the sentence comes from the `ledger.*` i18n keys. */
export function ledgerIcon(eventType: string): string {
  switch (eventType) {
    case 'CONTRIBUTION_RECEIVED':
      return 'south_west';
    case 'COLLECTION_SETTLED':
      return 'celebration';
    case 'PIGGY_BANK_CREDITED':
      return 'savings';
    case 'PIGGY_BANK_APPLIED_TO_COLLECTION':
      return 'north_east';
    case 'REMOVED_FROM_COLLECTION':
      return 'person_remove';
    default:
      return 'receipt_long';
  }
}

/** Money in = green, money parked in the piggy bank = gold, everything else = blue. */
export function ledgerTone(eventType: string): string {
  switch (eventType) {
    case 'CONTRIBUTION_RECEIVED':
      return 'ok';
    case 'PIGGY_BANK_CREDITED':
    case 'COLLECTION_SETTLED':
      return 'gold';
    default:
      return 'blue';
  }
}

/** In the app's chosen language, not the browser's locale - same reasoning as
 *  CollectionDetails.printedOnLabel. */
export function localeFor(lang: string | null | undefined): string {
  return lang === 'en' ? 'en-US' : 'pl-PL';
}
