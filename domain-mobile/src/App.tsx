import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Linking, Platform, Pressable, SafeAreaView, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import * as AuthSession from 'expo-auth-session';
import * as WebBrowser from 'expo-web-browser';
import * as Crypto from 'expo-crypto';
import { StatusBar } from 'expo-status-bar';
import { ApiClient, ApiFailure, type Criteria, type Property, searchParams } from './api';
import { storage } from './storage';

WebBrowser.maybeCompleteAuthSession();
const API = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:3000';
const IDENTITY = process.env.EXPO_PUBLIC_IDENTITY_URL || 'http://localhost:9000';
const api = new ApiClient(API, storage);
const EMPTY: Criteria = { q: '', suburb: '', listingType: '', minPrice: '', maxPrice: '', latitude: '', longitude: '', radiusKm: '' };
type Tab = 'search' | 'favorites' | 'inquiries' | 'searches' | 'alerts' | 'account';
const TABS: { id: Tab; title: string }[] = [{ id: 'search', title: 'Хайх' }, { id: 'favorites', title: 'Хадгалсан' }, { id: 'inquiries', title: 'Хүсэлт' }, { id: 'searches', title: 'Хайлт' }, { id: 'alerts', title: 'Мэдэгдэл' }, { id: 'account', title: 'Бүртгэл' }];

/** Displays a consistent accessible primary or secondary action on both native platforms. */
function Button({ title, onPress, disabled = false, secondary = false }: { title: string; onPress: () => void; disabled?: boolean; secondary?: boolean }) {
  return <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress} style={[styles.button, secondary && styles.secondary, disabled && styles.disabled]}><Text style={[styles.buttonText, secondary && styles.secondaryText]}>{title}</Text></Pressable>;
}
/** Keeps labels visible above controlled text fields rather than relying only on placeholders. */
function Input({ label, value, onChange, multiline = false }: { label: string; value: string; onChange: (value: string) => void; multiline?: boolean }) {
  return <View style={styles.field}><Text style={styles.label}>{label}</Text><TextInput accessibilityLabel={label} value={value} onChangeText={onChange} multiline={multiline} maxLength={multiline ? 2000 : 120} style={[styles.input, multiline && styles.textarea]} /></View>;
}
/** Renders the Mongolian buyer app with PKCE login and a server-managed, refreshable device session. */
export default function App() {
  const [tab, setTab] = useState<Tab>('search'); const [criteria, setCriteria] = useState<Criteria>(EMPTY);
  const [rows, setRows] = useState<any[]>([]); const [cursor, setCursor] = useState<string | null>(null);
  const [selected, setSelected] = useState<Property | null>(null); const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false); const [signedIn, setSignedIn] = useState(false);
  const [contact, setContact] = useState({ displayName: '', phone: '', email: '' });
  const [advanced, setAdvanced] = useState(false);
  const [inquiry, setInquiry] = useState(''); const [searchName, setSearchName] = useState(''); const [emailEnabled, setEmailEnabled] = useState(false);
  const loadVersion = useRef(0);
  const nonce = useMemo(() => Crypto.randomUUID(), []);
  const redirectUri = Platform.OS === 'web' ? `${window.location.origin}/oauth` : AuthSession.makeRedirectUri({ scheme: 'gerhub', path: 'oauth' });
  const discovery = { authorizationEndpoint: `${IDENTITY}/oauth2/authorize` };
  const [request, response, promptAsync] = AuthSession.useAuthRequest({ clientId: 'domain-mobile', redirectUri, scopes: ['openid', 'profile'], responseType: AuthSession.ResponseType.Code, usePKCE: true, extraParams: { nonce } }, discovery);
  const inquiryKey = useMemo(() => Crypto.randomUUID(), [selected?.id, inquiry]);

  /** Loads actual gateway records, appending a next page only for public discovery. */
  async function load(target = tab, next?: string) {
    const version = ++loadVersion.current; setBusy(true); setMessage('');
    try {
      if (target === 'account') { const result = await api.request<typeof contact>('/account'); if (version !== loadVersion.current) return; setContact(result); setRows([]); }
      else {
        const path = target === 'search' ? `/search?${searchParams(criteria, next)}` : `/${target}`;
        const result = await api.request<{ items: any[]; nextCursor?: string | null }>(path);
        if (version !== loadVersion.current) return;
        setRows(previous => next ? [...previous, ...result.items] : result.items); setCursor(result.nextCursor || null);
      }
    } catch (failure) { if (version !== loadVersion.current) return; if (failure instanceof ApiFailure && failure.status === 401) { setSignedIn(false); setContact({ displayName: '', phone: '', email: '' }); } setMessage(failure instanceof Error ? failure.message : 'Холбоос тасарлаа.'); setRows([]); }
    finally { if (version === loadVersion.current) setBusy(false); }
  }
  /** Completes code exchange without storing OAuth access/refresh credentials on the device. */
  useEffect(() => { if (response?.type !== 'success' || !request?.codeVerifier) return;
    void (async () => { try { await api.exchange(response.params.code, request.codeVerifier!, nonce, redirectUri); setSignedIn(true); setMessage('Амжилттай нэвтэрлээ.'); await load('search'); }
      catch (failure) { setMessage(failure instanceof Error ? failure.message : 'Нэвтрэх боломжгүй.'); } })();
  }, [response]);
  useEffect(() => { void storage.get().then(value => setSignedIn(Boolean(value))); void load(tab); }, [tab]);

  /** Applies one bounded mutation and displays a server-supplied error without losing retry keys. */
  async function mutate(path: string, method: string, body?: unknown, key?: string) {
    setBusy(true); try { await api.request(path, { method, body, key }); setMessage('Хүсэлтийг хүлээн авлаа.'); }
    catch (failure) { if (failure instanceof ApiFailure && failure.status === 401) setSignedIn(false); setMessage(failure instanceof Error ? failure.message : 'Холбоос тасарлаа.'); } finally { setBusy(false); }
  }
  /** Selects a current public detail instead of trusting a stale search projection. */
  async function detail(item: Property) { setBusy(true); try { setSelected(await api.request<Property>(`/properties/${item.id}`)); setMessage(''); } catch (failure) { setMessage(failure instanceof Error ? failure.message : 'Зар олдсонгүй.'); } finally { setBusy(false); } }
  /** Formats the API's currency without converting or relabeling stored values. */
  function price(item: Property) { return item.price === null ? 'Үнэ тохирно' : `${new Intl.NumberFormat('mn-MN').format(item.price)} ${item.currency === 'MNT' ? '₮' : 'AUD'}${item.listingType === 'RENT' ? ' / сар' : ''}`; }

  return <SafeAreaView style={styles.root}><StatusBar style="dark" /><View style={styles.header}><Text style={styles.brand}>GerHub</Text><Text style={styles.tagline}>Таны дараагийн орон зай</Text></View>
    <ScrollView horizontal style={styles.tabs} contentContainerStyle={styles.tabRow}>{TABS.map(item => <Pressable accessibilityRole="tab" accessibilityState={{ selected: tab === item.id }} key={item.id} style={[styles.tab, tab === item.id && styles.activeTab]} onPress={() => { setSelected(null); setTab(item.id); }}><Text style={styles.tabText}>{item.title}</Text></Pressable>)}</ScrollView>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      {!signedIn && <Button title="Нэвтрэх" disabled={!request || busy} onPress={() => { void promptAsync().catch(() => setMessage('Нэвтрэх цонх нээгдсэнгүй.')); }} />}
      {busy && <ActivityIndicator accessibilityLabel="Уншиж байна" color="#264f39" />}{message ? <Text accessibilityRole="alert" style={styles.notice}>{message}</Text> : null}
      {selected ? <View><Button title="← Зарууд руу" secondary onPress={() => setSelected(null)} /><View style={styles.placeholder}><Text style={styles.placeholderText}>⌂</Text><Text>Зураг нэмээгүй</Text></View><Text style={styles.heading}>{selected.title}</Text><Text style={styles.price}>{price(selected)}</Text><Text>{selected.address.suburb} · {selected.address.addressLine}</Text><Text>{selected.bedrooms ?? '—'} унтлагын өрөө · {selected.landSizeSqm ?? '—'} м²</Text>
        <Button title="Газрын зураг дээр үзэх" secondary onPress={() => { void Linking.openURL(`https://www.openstreetmap.org/?mlat=${selected.address.latitude}&mlon=${selected.address.longitude}#map=16/${selected.address.latitude}/${selected.address.longitude}`); }} />
        {signedIn && <><Button title="Зар хадгалах" secondary onPress={() => { void mutate(`/favorites/${selected.id}`, 'PUT'); }} /><Input label="Агент руу илгээх хүсэлт" multiline value={inquiry} onChange={setInquiry} /><Text style={styles.hint}>Таны бүртгэлийн нэр, имэйл, утсыг зарын агент хүлээн авна.</Text><Button title="Хүсэлт илгээх" disabled={busy || !inquiry.trim()} onPress={() => { void mutate(`/inquiries/${selected.id}`, 'POST', { message: inquiry }, inquiryKey); }} /></>}
      </View> : <>
        <Text style={styles.heading}>{TABS.find(item => item.id === tab)?.title}</Text>
        {tab === 'search' && <><Input label="Түлхүүр үг" value={criteria.q} onChange={q => setCriteria({ ...criteria, q })} /><Input label="Дүүрэг / байршил" value={criteria.suburb} onChange={suburb => setCriteria({ ...criteria, suburb })} />
          <View style={styles.row}><Button title="Бүгд" secondary onPress={() => setCriteria({ ...criteria, listingType: '' })} /><Button title="Худалдаа" secondary onPress={() => setCriteria({ ...criteria, listingType: 'SALE' })} /><Button title="Түрээс" secondary onPress={() => setCriteria({ ...criteria, listingType: 'RENT' })} /></View>
          <Button title={advanced ? 'Нэмэлт шүүлтүүр нуух' : 'Нэмэлт шүүлтүүр'} secondary onPress={() => setAdvanced(!advanced)} />
          {advanced && <><Input label="Доод үнэ (₮)" value={criteria.minPrice} onChange={minPrice => setCriteria({ ...criteria, minPrice })} /><Input label="Дээд үнэ (₮)" value={criteria.maxPrice} onChange={maxPrice => setCriteria({ ...criteria, maxPrice })} />
          <Input label="Өргөрөг (заавал биш)" value={criteria.latitude} onChange={latitude => setCriteria({ ...criteria, latitude })} /><Input label="Уртраг (заавал биш)" value={criteria.longitude} onChange={longitude => setCriteria({ ...criteria, longitude })} /><Input label="Радиус км (заавал биш)" value={criteria.radiusKm} onChange={radiusKm => setCriteria({ ...criteria, radiusKm })} /></>}
          <Button title="Зар хайх" disabled={busy} onPress={() => { void load('search'); }} />
          {signedIn && <><Input label="Хадгалах хайлтын нэр" value={searchName} onChange={setSearchName} /><View style={styles.row}><Text>Имэйлээр мэдэгдэх</Text><Switch value={emailEnabled} onValueChange={setEmailEnabled} /></View><Button title="Хайлт хадгалах" secondary disabled={!searchName.trim() || busy} onPress={() => { const params = Object.fromEntries(new URLSearchParams(searchParams(criteria))); void mutate('/searches', 'POST', { name: searchName, criteria: params, emailEnabled }); }} /></>}
        </>}
        {(tab === 'search' || tab === 'favorites') && rows.map((item: Property) => <View key={item.id} style={styles.card}><Pressable accessibilityRole="button" onPress={() => { void detail(item); }}><View style={styles.smallPlaceholder}><Text>⌂ · Зураг нэмээгүй</Text></View><Text style={styles.cardTitle}>{item.title}</Text><Text>{item.address.suburb} · {item.bedrooms ?? '—'} унтлагын өрөө</Text><Text style={styles.price}>{price(item)}</Text></Pressable>{tab === 'favorites' && <Button title="Хадгалснаас хасах" secondary onPress={() => { void mutate(`/favorites/${item.id}`, 'DELETE').then(() => load(tab)); }} />}</View>)}
        {tab === 'inquiries' && rows.map(item => <View key={item.id} style={styles.card}><Text style={styles.cardTitle}>{item.propertyTitle}</Text><Text>{item.message}</Text><Text style={styles.hint}>Төлөв: {({ OPEN: 'Шинэ', CONTACTED: 'Холбогдсон', CLOSED: 'Хаасан' } as Record<string, string>)[item.status]}</Text>{item.reply && <Text>Хариу: {item.reply}</Text>}</View>)}
        {tab === 'searches' && rows.map(item => <View key={item.id} style={styles.card}><Text style={styles.cardTitle}>{item.name}</Text><Button title="Энэ хайлтыг нээх" secondary onPress={() => { const restored = { ...EMPTY }; for (const key of Object.keys(restored) as (keyof Criteria)[]) restored[key] = String(item.criteria[key] ?? ''); setCriteria(restored); setTab('search'); }} /><Button title="Устгах" secondary onPress={() => { void mutate(`/searches/${item.id}`, 'DELETE').then(() => load(tab)); }} /></View>)}
        {tab === 'alerts' && rows.map(item => <View key={item.id} style={styles.card}><Text style={styles.cardTitle}>{item.title}</Text><Button title="Зар үзэх" onPress={() => { void api.request<Property>(`/properties/${item.propertyId}`).then(setSelected).catch(() => setMessage('Зар одоо харагдахгүй байна.')); }} />{!item.readAt && <Button title="Уншсан гэж тэмдэглэх" secondary onPress={() => { void mutate(`/alerts/${item.id}`, 'POST').then(() => load(tab)); }} />}</View>)}
        {tab === 'account' && <><Text>{contact.email}</Text><Input label="Нэр" value={contact.displayName} onChange={displayName => setContact({ ...contact, displayName })} /><Input label="Утас" value={contact.phone} onChange={phone => setContact({ ...contact, phone })} /><Button title="Бүртгэл хадгалах" disabled={!signedIn || busy} onPress={() => { void mutate('/account', 'PUT', { displayName: contact.displayName, phone: contact.phone }); }} />
          {signedIn && <Button title="Гарах" secondary onPress={() => { void api.logout().then(() => { setSignedIn(false); setContact({ displayName: '', phone: '', email: '' }); setRows([]); setTab('search'); }).catch(() => { setSignedIn(false); setMessage('Төхөөрөмжөөс гарлаа. Серверийн холбоосыг шалгана уу.'); }); }} />}
          <Button title="Бүртгүүлэх" secondary onPress={() => { void Linking.openURL(`${API}/register`); }} /><Button title="Нууц үг сэргээх" secondary onPress={() => { void Linking.openURL(`${API}/forgot-password`); }} />
        </>}
        {cursor && tab === 'search' && <Button title="Дараагийн зарууд" secondary disabled={busy} onPress={() => { void load(tab, cursor); }} />}
        {!rows.length && !busy && tab !== 'account' && <Text style={styles.hint}>Одоогоор харагдах зүйл алга.</Text>}
      </>}
    </ScrollView>
  </SafeAreaView>;
}
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#fafaf7' }, header: { padding: 20, borderBottomWidth: 1, borderBottomColor: '#e4e7e2' }, brand: { fontSize: 32, fontWeight: '700', color: '#264f39' }, tagline: { color: '#666d65', marginTop: 4 },
  tabs: { flexGrow: 0, flexShrink: 0, height: 60, minHeight: 60 }, tabRow: { padding: 10, gap: 8 }, tab: { paddingHorizontal: 12, paddingVertical: 10, borderRadius: 8, backgroundColor: '#eef0eb' }, activeTab: { backgroundColor: '#d9e8dc' }, tabText: { color: '#264f39', fontWeight: '600' },
  content: { padding: 20, gap: 12, maxWidth: 760, width: '100%', alignSelf: 'center' }, heading: { fontSize: 28, color: '#23362a', fontWeight: '700', marginVertical: 12 }, field: { marginBottom: 10 }, label: { fontSize: 13, color: '#49564b', marginBottom: 6 },
  input: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#d4dad3', borderRadius: 8, padding: 12, fontSize: 16, color: '#23362a' }, textarea: { minHeight: 110, textAlignVertical: 'top' },
  button: { backgroundColor: '#264f39', padding: 13, borderRadius: 8, marginVertical: 6, alignItems: 'center' }, buttonText: { color: '#fff', fontSize: 15, fontWeight: '600' }, secondary: { backgroundColor: '#f5f7f2', borderWidth: 1, borderColor: '#c7d5c8' }, secondaryText: { color: '#264f39' }, disabled: { opacity: 0.5 },
  card: { backgroundColor: '#fff', padding: 16, borderWidth: 1, borderColor: '#e0e5dd', borderRadius: 12, marginTop: 10 }, cardTitle: { fontSize: 18, fontWeight: '600', color: '#23362a', marginVertical: 10 }, price: { fontSize: 21, color: '#264f39', fontWeight: '700', marginVertical: 10 },
  placeholder: { minHeight: 190, alignItems: 'center', justifyContent: 'center', backgroundColor: '#eaf0e7', borderRadius: 12 }, placeholderText: { fontSize: 60, color: '#648266' }, smallPlaceholder: { height: 80, alignItems: 'center', justifyContent: 'center', backgroundColor: '#eef3ea', borderRadius: 8 },
  row: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 10 }, hint: { color: '#6b7368', fontSize: 13, marginVertical: 6 }, notice: { padding: 12, backgroundColor: '#e8f1e6', color: '#264f39', borderRadius: 8 },
});
