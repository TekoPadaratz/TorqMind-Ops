export const FUELS = [
  { value: 'DIESEL_S10', label: 'Diesel S-10' },
  { value: 'DIESEL_S500', label: 'Diesel S-500' },
  { value: 'ETANOL', label: 'Etanol' },
  { value: 'GASOLINA_ADITIVADA', label: 'Gasolina Aditivada' },
  { value: 'GASOLINA_COMUM', label: 'Gasolina Comum' }
] as const;

export type FuelValue = (typeof FUELS)[number]['value'];

export function showsGasolineAlcohol(fuel: string | null | undefined): boolean {
  return fuel === 'GASOLINA_COMUM' || fuel === 'GASOLINA_ADITIVADA';
}

export function showsAehcAlcohol(fuel: string | null | undefined): boolean {
  return fuel === 'ETANOL';
}

export function qualityAnalysisPath(fuel?: string | null): string {
  return fuel ? `/occurrences/new/fuel-quality?fuel=${encodeURIComponent(fuel)}` : '/occurrences/new/fuel-quality';
}
