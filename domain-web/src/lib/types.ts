export type Currency = 'MNT' | 'AUD';
export type PropertyType = 'HOUSE' | 'APARTMENT' | 'TOWNHOUSE' | 'LAND' | 'UNIT';
export type ListingType = 'SALE' | 'RENT';
export type PropertyStatus = 'DRAFT' | 'ACTIVE' | 'UNDER_OFFER' | 'SOLD' | 'RENTED' | 'WITHDRAWN';
export interface PropertyAddress {
  addressLine: string; suburb: string; state: string; postcode: string; latitude: number; longitude: number;
}
export interface Property {
  id: string; agentId: string; title: string; propertyType: PropertyType; listingType: ListingType;
  price: number | null; currency: Currency; bedrooms: number | null; bathrooms: number | null;
  parkingSpaces: number | null; landSizeSqm: number | null; address: PropertyAddress;
  status: PropertyStatus; version: number; createdAt: string; updatedAt: string;
}
export interface PropertyPage { items: Property[]; nextCursor: string | null; pageSize: number }
export type PropertyInput = Pick<Property, 'title' | 'propertyType' | 'listingType' | 'price' | 'currency'
  | 'bedrooms' | 'bathrooms' | 'parkingSpaces' | 'landSizeSqm' | 'address'>;
export interface Session {
  clientId?: string; userId: string; roles: string[]; accessToken: string; refreshToken: string; idToken: string; expiresAt: number;
}
export interface Problem { title?: string; detail?: string; status?: number; traceId?: string }
