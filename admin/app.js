import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js';
import { getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js';
import { getFirestore, doc, getDoc, setDoc } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-firestore.js';
import { getStorage, ref, uploadBytes, deleteObject } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-storage.js';
import { firebaseConfig } from './firebase-config.js';

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const storage = getStorage(app);
const catalogRef = doc(db, 'catalog', 'current');
const $ = (id) => document.getElementById(id);
let catalog = { videos: [], routines: [] };
let savedPaths = new Set();
let busy = false;

function message(text, type = '') {
  $('status').textContent = text;
  $('status').className = type;
}
function button(text, action, cls = 'small secondary') {
  const el = document.createElement('button');
  el.type = 'button'; el.textContent = text; el.className = cls;
  el.addEventListener('click', action);
  return el;
}
function input(value, onChange, placeholder = '') {
  const el = document.createElement('input');
  el.value = value || ''; el.placeholder = placeholder;
  el.addEventListener('input', () => onChange(el.value));
  return el;
}
function label(text, control) {
  const el = document.createElement('label');
  el.append(document.createTextNode(text), control);
  return el;
}
function move(list, index, offset) {
  const next = index + offset;
  if (next < 0 || next >= list.length) return;
  [list[index], list[next]] = [list[next], list[index]];
  render();
}
function fileInput(accept, onChange) {
  const el = document.createElement('input');
  el.type = 'file'; el.accept = accept;
  el.addEventListener('change', () => onChange(el.files[0] || null));
  return el;
}
function renderVideos() {
  const host = $('videos'); host.replaceChildren();
  catalog.videos.forEach((video, index) => {
    const row = document.createElement('div'); row.className = 'item';
    const top = document.createElement('div'); top.className = 'itemRow';
    const actions = document.createElement('div'); actions.className = 'actions';
    actions.append(button('↑', () => move(catalog.videos, index, -1)), button('↓', () => move(catalog.videos, index, 1)), button('Quitar', () => { catalog.videos.splice(index, 1); render(); }, 'small danger'));
    top.append(label('Título', input(video.title, value => video.title = value, 'Ej. Nuestra canción')), actions);
    row.append(top);
    if (!video.storagePath) {
      row.append(label('Archivo MP4', fileInput('video/mp4', file => video.file = file)));
      if (video.file) { const p = document.createElement('p'); p.className = 'hint'; p.textContent = `Listo para subir: ${video.file.name}`; row.append(p); }
    }
    else { const p = document.createElement('p'); p.className = 'hint'; p.textContent = 'Video cargado. Para reemplazarlo, quitá este y agregá uno nuevo.'; row.append(p); }
    host.append(row);
  });
  if (!catalog.videos.length) host.textContent = 'Todavía no hay videos.';
}
function renderRoutines() {
  const host = $('routines'); host.replaceChildren();
  catalog.routines.forEach((routine, index) => {
    const card = document.createElement('div'); card.className = 'item';
    const top = document.createElement('div'); top.className = 'itemRow';
    const actions = document.createElement('div'); actions.className = 'actions';
    actions.append(button('↑', () => move(catalog.routines, index, -1)), button('↓', () => move(catalog.routines, index, 1)), button('Quitar', () => { catalog.routines.splice(index, 1); render(); }, 'small danger'));
    top.append(label('Nombre de la rutina', input(routine.title, value => routine.title = value, 'Ej. Prepararse para dormir')), actions);
    card.append(top);
    routine.steps.forEach((step, stepIndex) => {
      const box = document.createElement('div'); box.className = 'step';
      const heading = document.createElement('div'); heading.className = 'stepTitle';
      const title = document.createElement('strong'); title.textContent = `Paso ${stepIndex + 1}`;
      const stepActions = document.createElement('div'); stepActions.className = 'actions';
      stepActions.append(button('↑', () => move(routine.steps, stepIndex, -1)), button('↓', () => move(routine.steps, stepIndex, 1)), button('Quitar', () => { routine.steps.splice(stepIndex, 1); render(); }, 'small danger'));
      heading.append(title, stepActions);
      box.append(heading, label('Instrucción', input(step.text, value => step.text = value, 'Ej. Lavarse las manos')));
      box.append(label(step.imagePath ? 'Cambiar imagen (opcional)' : 'Imagen (opcional)', fileInput('image/jpeg,image/png', file => step.file = file)));
      if (step.imagePath) box.append(button('Quitar imagen', () => { step.imagePath = ''; step.file = null; render(); }, 'small danger'));
      card.append(box);
    });
    card.append(button('Agregar paso', () => { routine.steps.push({ text: '', imagePath: '' }); render(); }));
    host.append(card);
  });
  if (!catalog.routines.length) host.textContent = 'Todavía no hay rutinas.';
}
function render() { renderVideos(); renderRoutines(); }
function paths(data) {
  return new Set([...data.videos.map(v => v.storagePath), ...data.routines.flatMap(r => r.steps.map(s => s.imagePath))].filter(Boolean));
}
function validate() {
  for (const video of catalog.videos) {
    if (!video.title.trim()) throw Error('Cada video necesita un título.');
    if (!video.storagePath && !video.file) throw Error(`Falta el archivo de “${video.title}”.`);
    if (video.file && (video.file.type !== 'video/mp4' || video.file.size >= 500 * 1024 * 1024)) throw Error('Los videos deben ser MP4 y medir menos de 500 MB.');
  }
  for (const routine of catalog.routines) {
    if (!routine.title.trim() || !routine.steps.length) throw Error('Cada rutina necesita un nombre y al menos un paso.');
    for (const step of routine.steps) {
      if (!step.text.trim()) throw Error(`Hay un paso sin instrucción en “${routine.title}”.`);
      if (step.file && (!['image/jpeg', 'image/png'].includes(step.file.type) || step.file.size >= 10 * 1024 * 1024)) throw Error('Las imágenes deben ser JPG o PNG de menos de 10 MB.');
    }
  }
}
async function save() {
  if (busy) return;
  try {
    validate(); busy = true; $('save').disabled = true;
    const uploaded = [];
    try {
      for (const video of catalog.videos) if (video.file) {
        message(`Subiendo video: ${video.title}`);
        const path = `videos/${crypto.randomUUID()}.mp4`;
        await uploadBytes(ref(storage, path), video.file, { contentType: 'video/mp4' });
        uploaded.push(path); video.storagePath = path;
      }
      for (const routine of catalog.routines) for (const step of routine.steps) if (step.file) {
        message(`Subiendo imagen: ${routine.title}`);
        const ext = step.file.type === 'image/png' ? 'png' : 'jpg';
        const path = `images/${crypto.randomUUID()}.${ext}`;
        await uploadBytes(ref(storage, path), step.file, { contentType: step.file.type });
        uploaded.push(path); step.imagePath = path;
      }
      const data = {
        videos: catalog.videos.map(v => ({ id: v.id, title: v.title.trim(), storagePath: v.storagePath })),
        routines: catalog.routines.map(r => ({ id: r.id, title: r.title.trim(), steps: r.steps.map(s => ({ text: s.text.trim(), imagePath: s.imagePath || '' })) }))
      };
      await setDoc(catalogRef, data);
      const current = paths(data);
      const removed = [...savedPaths].filter(path => !current.has(path));
      savedPaths = current; catalog = data; render();
      message('Cambios guardados. La TV los recibirá cuando tenga conexión.', 'ok');
      await Promise.allSettled(removed.map(path => deleteObject(ref(storage, path))));
    } catch (error) {
      await Promise.allSettled(uploaded.map(path => deleteObject(ref(storage, path))));
      throw error;
    }
  } catch (error) { message(error.message || 'No se pudieron guardar los cambios.', 'error'); }
  finally { busy = false; $('save').disabled = false; }
}

$('loginForm').addEventListener('submit', async event => {
  event.preventDefault();
  try { message('Ingresando…'); await signInWithEmailAndPassword(auth, $('email').value.trim(), $('password').value); }
  catch { message('No se pudo ingresar. Revisá el correo y la contraseña.', 'error'); }
});
$('logout').addEventListener('click', () => signOut(auth));
$('addVideo').addEventListener('click', () => { catalog.videos.push({ id: crypto.randomUUID(), title: '', storagePath: '' }); render(); });
$('addRoutine').addEventListener('click', () => { catalog.routines.push({ id: crypto.randomUUID(), title: '', steps: [{ text: '', imagePath: '' }] }); render(); });
$('save').addEventListener('click', save);
onAuthStateChanged(auth, async user => {
  $('login').hidden = !!user; $('editor').hidden = true; $('logout').hidden = !user;
  if (!user) { message(''); return; }
  try {
    const role = await getDoc(doc(db, 'roles', user.uid));
    if (role.data()?.role !== 'admin') throw Error('Esta cuenta no tiene permiso de administrador.');
    const snapshot = await getDoc(catalogRef);
    const data = snapshot.exists() ? snapshot.data() : {};
    catalog = { videos: Array.isArray(data.videos) ? data.videos : [], routines: Array.isArray(data.routines) ? data.routines : [] };
    savedPaths = paths(catalog); render(); $('editor').hidden = false; message('');
  } catch (error) { message(error.message || 'No se pudo cargar el contenido.', 'error'); }
});
