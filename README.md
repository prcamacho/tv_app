# TV en familia

App privada para el Google TV BGH B5024US6G. La primera versión muestra el mismo contenido a los tres chicos: videos familiares y rutinas visuales paso a paso. Los adultos administran ese contenido desde una página web.

## Cómo funciona

- **En la TV:** una pantalla simple, manejable con el control remoto, separa videos y rutinas. Cada rutina muestra una imagen y una instrucción por paso. No hay anuncios ni enlaces a contenido ajeno.
- **En el panel de adultos:** se agregan, ordenan o quitan videos y rutinas. El acceso requiere una cuenta de adulto.
- **Sin reinstalar:** los cambios de contenido se guardan en Cloud Firestore y aparecen en la TV cuando tiene conexión. Las fotos y los videos se guardan en Cloud Storage for Firebase. La TV descarga los archivos que se abren y conserva esa copia para volver a usarlos sin conexión, sujeto al espacio disponible.
- **Actualizaciones de la app:** solo hacen falta al cambiar funciones o diseño. Si más adelante se publica mediante Google Play, se pueden activar las actualizaciones automáticas.

## Estructura

- `tv/`: aplicación Android TV nativa.
- `admin/`: panel web para adultos.
- `firestore.rules` y `storage.rules`: permisos de lectura y edición.

## Configuración pendiente para ponerla en funcionamiento

1. Crear un proyecto Firebase y habilitar **Authentication (correo y contraseña)**, **Cloud Firestore** y **Cloud Storage**. Storage requiere el plan Blaze, con facturación habilitada; revisar precios y establecer alertas antes de subir videos.
2. Registrar la app Android con el paquete `ar.com.prcamacho.tvapp`, descargar `google-services.json` y colocarlo en `tv/google-services.json`.
3. Registrar una app web en el mismo proyecto y copiar sus datos públicos a `admin/firebase-config.js` siguiendo `admin/firebase-config.example.js`.
4. Crear en Authentication dos usuarios con correo y contraseña: uno para el panel y otro para la TV. Copiar sus UID a documentos de Firestore: `roles/<UID del adulto>` con `{ "role": "admin" }` y `roles/<UID de TV>` con `{ "role": "viewer" }`.
5. Publicar las reglas incluidas en este repositorio desde Firebase Console. Las reglas niegan todo acceso sin una cuenta y un rol permitido. Al publicar las reglas de Storage que consultan Firestore, aceptar el permiso de conexión entre ambos servicios que solicita Firebase.
6. Publicar `admin/` como sitio estático con HTTPS (por ejemplo Firebase Hosting) y abrir el proyecto Android en Android Studio para compilar e instalar la app en la TV.

Los archivos de configuración local y las claves de firma no se suben al repositorio. La configuración web de Firebase no es una contraseña: la protección real está en Authentication y las reglas.

## Alcance de esta primera versión

- Videos propios en MP4 (preferentemente H.264 y AAC), hasta 500 MB cada uno.
- Pasos de rutina con texto e imagen JPEG/PNG opcional.
- Catálogo común a los tres chicos; no se necesitan perfiles.
- Uso con flechas, OK y Atrás del control remoto.

Todavía requiere configurar el proyecto Firebase, compilar la app, instalarla y probarla en el BGH. La compatibilidad exacta del televisor se verifica con esa prueba física.

## Referencias técnicas

- [Guía oficial para apps de TV](https://developer.android.com/training/tv/get-started)
- [Sincronización y caché sin conexión de Firestore](https://firebase.google.com/docs/firestore/manage-data/enable-offline)
- [Requisito de facturación para Cloud Storage for Firebase](https://firebase.google.com/docs/storage/web/start)
- [Actualizaciones automáticas de apps en Google Play](https://support.google.com/googleplay/answer/113412)
