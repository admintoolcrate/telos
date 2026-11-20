/* The ToolCrate family website: the light/dark toggle, sections that drift in, and the phone on
   the home page (swipe between the app's screens, or use the dots, the arrows or the arrow keys).
   This file is the same in Lyceum, Telos and Kairos (docs/assets/site.js); change it in all three. */
(function () {
  var root = document.documentElement, btn = document.getElementById('theme');
  var key = root.getAttribute('data-theme-key') || 'theme';
  function current() { return root.getAttribute('data-theme') || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'); }
  if (btn) btn.addEventListener('click', function () {
    var t = current() === 'dark' ? 'light' : 'dark';
    root.setAttribute('data-theme', t);
    try { localStorage.setItem(key, t); } catch (e) {}
  });

  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } });
    }, { rootMargin: '0px 0px -8% 0px' });
    document.querySelectorAll('.reveal').forEach(function (el) { io.observe(el); });
  }

  var slides = document.getElementById('slides');
  if (!slides) return;
  var all = [].slice.call(slides.children);
  var dots = [].slice.call(document.querySelectorAll('.dot-btn'));
  var prev = document.getElementById('prev'), next = document.getElementById('next');
  var cap = document.getElementById('caption-name'), desc = document.getElementById('caption-desc');
  var reduced = function () { return matchMedia('(prefers-reduced-motion: reduce)').matches; };
  var go = function (i) { slides.scrollTo({ left: i * slides.clientWidth, behavior: reduced() ? 'auto' : 'smooth' }); };
  var at = function () { return Math.max(0, Math.min(all.length - 1, Math.round(slides.scrollLeft / slides.clientWidth))); };
  var shown = -1;
  var sync = function () {
    var i = at();
    if (i === shown) return;
    shown = i;
    dots.forEach(function (d, j) { if (j === i) d.setAttribute('aria-current', 'true'); else d.removeAttribute('aria-current'); });
    prev.disabled = i === 0; next.disabled = i === all.length - 1;
    if (cap) cap.textContent = all[i].getAttribute('data-name') || '';
    if (desc) desc.textContent = all[i].getAttribute('data-desc') || '';
  };
  dots.forEach(function (d, i) { d.addEventListener('click', function () { go(i); }); });
  slides.addEventListener('scroll', sync, { passive: true });
  prev.addEventListener('click', function () { go(Math.max(at() - 1, 0)); });
  next.addEventListener('click', function () { go(Math.min(at() + 1, all.length - 1)); });
  slides.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowRight') { go(Math.min(at() + 1, all.length - 1)); e.preventDefault(); }
    if (e.key === 'ArrowLeft') { go(Math.max(at() - 1, 0)); e.preventDefault(); }
  });
  /* Drag with a mouse, too: phones swipe natively. */
  var dragX = null, startLeft = 0;
  slides.addEventListener('pointerdown', function (e) {
    if (e.pointerType !== 'mouse') return;
    dragX = e.clientX; startLeft = slides.scrollLeft; slides.style.scrollSnapType = 'none';
  });
  addEventListener('pointermove', function (e) { if (dragX !== null) slides.scrollLeft = startLeft - (e.clientX - dragX); });
  addEventListener('pointerup', function (e) {
    if (dragX === null) return;
    var moved = e.clientX - dragX; dragX = null; slides.style.scrollSnapType = '';
    var i = Math.round(startLeft / slides.clientWidth) + (moved < -40 ? 1 : moved > 40 ? -1 : 0);
    go(Math.max(0, Math.min(all.length - 1, i)));
  });
  sync();
})();
