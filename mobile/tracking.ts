import * as Location from 'expo-location';
import * as SecureStore from 'expo-secure-store';
import * as TaskManager from 'expo-task-manager';

export const LOCATION_TASK = 'exame-perto-active-delivery-location';
const SESSION_KEY = 'exame-perto-mobile-session';
const CONTEXT_KEY = 'exame-perto-tracking-context';
const QUEUE_KEY = 'exame-perto-tracking-queue';
const API = process.env.EXPO_PUBLIC_API_URL ?? 'http://127.0.0.1:8080/api/v1';
const MAX_QUEUE = 8;
const MAX_AGE_MS = 120_000;

export type TrackingContext = { assignmentId: string; orderId: string; nextSequence: number };
export type PendingPosition = {
  orderId: string;
  assignmentId: string;
  sequence: number;
  capturedAt: string;
  latitude: number;
  longitude: number;
  accuracyMeters: number;
};
type Session = { accessToken: string; refreshToken: string };

export async function saveContext(context: TrackingContext) {
  await SecureStore.setItemAsync(CONTEXT_KEY, JSON.stringify(context));
}

export async function clearTrackingStorage() {
  await Promise.all([
    SecureStore.deleteItemAsync(CONTEXT_KEY),
    SecureStore.deleteItemAsync(QUEUE_KEY),
  ]);
}

export async function activeTrackingContext(): Promise<TrackingContext | null> {
  const raw = await SecureStore.getItemAsync(CONTEXT_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw) as TrackingContext; } catch { return null; }
}

async function readQueue(): Promise<PendingPosition[]> {
  const raw = await SecureStore.getItemAsync(QUEUE_KEY);
  if (!raw) return [];
  try {
    return JSON.parse(raw) as PendingPosition[];
  } catch {
    await SecureStore.deleteItemAsync(QUEUE_KEY);
    return [];
  }
}

async function writeQueue(values: PendingPosition[]) {
  const cutoff = Date.now() - MAX_AGE_MS;
  const recent = values.filter((value) => Date.parse(value.capturedAt) >= cutoff).slice(-MAX_QUEUE);
  if (recent.length) await SecureStore.setItemAsync(QUEUE_KEY, JSON.stringify(recent));
  else await SecureStore.deleteItemAsync(QUEUE_KEY);
  return recent;
}

async function session(): Promise<Session | null> {
  const raw = await SecureStore.getItemAsync(SESSION_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw) as Session; } catch { return null; }
}

async function refresh(current: Session): Promise<Session | null> {
  try {
    const response = await fetch(`${API}/auth/refresh`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: current.refreshToken }),
    });
    if (!response.ok) return null;
    const next = await response.json() as Session;
    await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify(next));
    return next;
  } catch { return null; }
}

async function send(point: PendingPosition, current: Session): Promise<'sent' | 'retry' | 'revoked'> {
  const request = (token: string) => fetch(`${API}/orders/${point.orderId}/locations`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(point),
  });
  try {
    let response = await request(current.accessToken);
    if (response.status === 401) {
      const renewed = await refresh(current);
      if (!renewed) return 'revoked';
      response = await request(renewed.accessToken);
    }
    if (response.ok) return 'sent';
    if ([401, 403, 404, 409, 422].includes(response.status)) return 'revoked';
    return 'retry';
  } catch { return 'retry'; }
}

export async function flushQueue() {
  const contextRaw = await SecureStore.getItemAsync(CONTEXT_KEY);
  if (!contextRaw) return;
  let current = await session();
  if (!current) { await clearTrackingStorage(); return; }
  let queue = await writeQueue(await readQueue());
  while (queue.length) {
    const result = await send(queue[0], current);
    if (result === 'sent') {
      current = await session() ?? current;
      queue = await writeQueue(queue.slice(1));
    }
    else if (result === 'revoked') { await stopBackgroundTracking(); return; }
    else break;
  }
}

async function enqueue(locations: Location.LocationObject[]) {
  const contextRaw = await SecureStore.getItemAsync(CONTEXT_KEY);
  if (!contextRaw) return;
  let context: TrackingContext;
  try { context = JSON.parse(contextRaw) as TrackingContext; } catch { return; }
  const current = await readQueue();
  const nextSequence = context.nextSequence;
  const points = locations.flatMap((location, index) => {
    const accuracy = location.coords.accuracy;
    if (accuracy == null) return [];
    return [{ orderId: context.orderId, assignmentId: context.assignmentId, sequence: nextSequence + index, capturedAt: new Date(location.timestamp).toISOString(),
      latitude: location.coords.latitude, longitude: location.coords.longitude, accuracyMeters: accuracy }];
  });
  context.nextSequence = nextSequence + points.length;
  await SecureStore.setItemAsync(CONTEXT_KEY, JSON.stringify(context));
  await writeQueue([...current, ...points]);
  await flushQueue();
}

TaskManager.defineTask(LOCATION_TASK, async ({ data, error }) => {
  if (error || !data) return;
  const locations = (data as { locations?: Location.LocationObject[] }).locations ?? [];
  await enqueue(locations);
});

export async function startBackgroundTracking(context: TrackingContext) {
  const available = await TaskManager.isAvailableAsync();
  if (!available) throw new Error('O rastreamento em segundo plano exige um development build; não está disponível no Expo Go.');
  const foreground = await Location.requestForegroundPermissionsAsync();
  if (foreground.status !== 'granted') throw new Error('Permissão de localização durante a tarefa não concedida.');
  const background = await Location.requestBackgroundPermissionsAsync();
  if (background.status !== 'granted') throw new Error('Permissão de localização em segundo plano não concedida.');
  await saveContext(context);
  await SecureStore.setItemAsync(QUEUE_KEY, JSON.stringify([]));
  await Location.startLocationUpdatesAsync(LOCATION_TASK, {
    accuracy: Location.Accuracy.Balanced,
    timeInterval: 15_000,
    distanceInterval: 0,
    deferredUpdatesInterval: 15_000,
    deferredUpdatesDistance: 0,
    showsBackgroundLocationIndicator: true,
    foregroundService: {
      notificationTitle: 'Exame Perto: localização ativa',
      notificationBody: 'A localização é compartilhada somente durante a entrega ativa.',
      killServiceOnDestroy: true,
    },
  });
  await flushQueue();
}

export async function stopBackgroundTracking() {
  try {
    if (await Location.hasStartedLocationUpdatesAsync(LOCATION_TASK)) await Location.stopLocationUpdatesAsync(LOCATION_TASK);
  } finally { await clearTrackingStorage(); }
}

export async function trackingRegistered() {
  return Location.hasStartedLocationUpdatesAsync(LOCATION_TASK);
}
