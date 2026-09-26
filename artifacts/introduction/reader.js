const input = document.querySelector('#search');
const panels = [...document.querySelectorAll('.panel')];
const status = document.querySelector('#search-status');
function search() {
  const query = input.value.trim().toLocaleLowerCase();
  let count = 0;
  panels.forEach(panel => {
    panel.hidden = !panel.textContent.toLocaleLowerCase().includes(query);
    if (!panel.hidden) count++;
  });
  status.textContent = `${count} of ${panels.length} panels${query ? ' match your search' : ' · source excerpts expand below each panel'}`;
  document.querySelector('#no-results').hidden = count !== 0;
}
input.addEventListener('input', search);
document.querySelector('#clear').addEventListener('click', () => {input.value = ''; search(); input.focus();});
document.querySelector('#print').addEventListener('click', () => window.print());
document.querySelector('#policy').addEventListener('change', event => {
  document.querySelectorAll('#routes tbody tr').forEach(row => {
    row.hidden = !row.cells[2].textContent.includes(event.target.value);
  });
});
document.querySelectorAll('nav a, .start').forEach(link => link.addEventListener('click', () => {
  input.value = ''; search();
}));
window.addEventListener('hashchange', () => {
  const target = document.getElementById(location.hash.slice(1));
  if (target?.hidden) { input.value = ''; search(); target.scrollIntoView(); }
});
