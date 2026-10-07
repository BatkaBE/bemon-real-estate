import type { Currency, PropertyStatus, PropertyType } from './types';
export const propertyTypes: Record<PropertyType, string> = {
  APARTMENT: 'Орон сууц', HOUSE: 'Хувийн сууц', TOWNHOUSE: 'Таунхаус', LAND: 'Газар', UNIT: 'Жижиг сууц',
};
export const statuses: Record<PropertyStatus, string> = {
  DRAFT: 'Ноорог', ACTIVE: 'Нийтэлсэн', UNDER_OFFER: 'Хэлэлцэж буй', SOLD: 'Зарагдсан', RENTED: 'Түрээслэгдсэн', WITHDRAWN: 'Татан авсан',
};
/** Formats the property's own currency without converting or relabelling legacy prices. */
export function formatPrice(value: number | null, currency: Currency): string {
  if (value === null) return 'Үнэ тохиролцоно';
  return new Intl.NumberFormat('mn-MN', { style: 'currency', currency, maximumFractionDigits: 2,
    minimumFractionDigits: currency === 'MNT' ? 0 : 2 }).format(value);
}
