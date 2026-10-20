/* ==========================================================================
   e4mc-inspired Light Style Reference (Campfire Edition) — JS
   ========================================================================== */
(function () {
  "use strict";

  var prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ----- Header scroll state ----- */
  var header = document.getElementById("site-header");
  function onScroll() {
    if (window.scrollY > 10) {
      header.classList.add("scrolled");
    } else {
      header.classList.remove("scrolled");
    }
  }
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  /* ----- Theme toggle (Aether dark <-> light) ----- */
  var themeToggle = document.getElementById("theme-toggle");
  if (themeToggle) {
    themeToggle.addEventListener("click", function () {
      var root = document.documentElement;
      var next = root.getAttribute("data-theme") === "light" ? "dark" : "light";
      root.setAttribute("data-theme", next);
      try { localStorage.setItem("campfire-theme", next); } catch (e) { /* no-op */ }
    });
  }

  /* ----- Mobile navigation toggle ----- */
  var toggle = document.getElementById("nav-toggle");
  var links = document.getElementById("nav-links");
  if (toggle && links) {
    toggle.addEventListener("click", function () {
      var expanded = toggle.getAttribute("aria-expanded") === "true";
      toggle.setAttribute("aria-expanded", String(!expanded));
      links.classList.toggle("open");
    });
    links.addEventListener("click", function (e) {
      if (e.target.closest("a")) {
        links.classList.remove("open");
        toggle.setAttribute("aria-expanded", "false");
      }
    });
  }

  /* ----- Scroll reveal (IntersectionObserver) ----- */
  var reveals = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window && !prefersReducedMotion) {
    var observer = new IntersectionObserver(
      function (entries, observer) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.15, rootMargin: "0px 0px -10% 0px" }
    );
    reveals.forEach(function (el) {
      observer.observe(el);
    });
  } else {
    reveals.forEach(function (el) {
      el.classList.add("is-visible");
    });
  }

  /* ----- Floating ember particles (campfire backdrop) ----- */
  var canvas = document.getElementById("embers");
  if (canvas && canvas.getContext && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    var ctx = canvas.getContext("2d");
    var w = 0, h = 0, embers = [], rafId = null;
    var theme = document.documentElement.getAttribute("data-theme") || "dark";

    // keep in sync with the light/dark toggle
    if ("MutationObserver" in window) {
      new MutationObserver(function () {
        theme = document.documentElement.getAttribute("data-theme") || "dark";
      }).observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
    }

    function rand(min, max) { return min + Math.random() * (max - min); }

    function spawn(seed) {
      return {
        x: rand(0, w),
        y: seed ? rand(0, h) : h + rand(4, 40),
        r: rand(0.8, 2.8),
        vy: rand(0.25, 0.9),
        sway: rand(0.3, 1.2),
        phase: rand(0, Math.PI * 2),
        alpha: rand(0.3, 0.85),
        hue: rand(16, 38),
        life: 0,
        ttl: rand(300, 760)
      };
    }

    function resize() {
      var dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = window.innerWidth;
      h = window.innerHeight;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      var target = Math.min(85, Math.max(25, Math.floor((w * h) / 22000)));
      embers = [];
      for (var i = 0; i < target; i++) embers.push(spawn(true));
    }

    function draw(p, fade) {
      var a = Math.max(0, p.alpha * fade);
      ctx.beginPath();
      if (theme === "light") {
        // deep ember on parchment — plain source-over so it reads on cream
        ctx.fillStyle = "hsla(" + (p.hue + 4) + ", 85%, 42%, " + (a * 0.5).toFixed(3) + ")";
      } else {
        // glowing spark on charcoal
        ctx.fillStyle = "hsla(" + p.hue + ", 100%, 60%, " + a.toFixed(3) + ")";
      }
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fill();
    }

    function step() {
      ctx.clearRect(0, 0, w, h);
      ctx.globalCompositeOperation = theme === "light" ? "source-over" : "lighter";
      for (var i = 0; i < embers.length; i++) {
        var p = embers[i];
        p.life++;
        p.y -= p.vy;
        p.phase += 0.02 * p.sway;
        p.x += Math.sin(p.phase) * 0.35;
        if (p.y < -10 || p.life >= p.ttl) {
          embers[i] = spawn(false);
          continue;
        }
        draw(p, 1 - p.life / p.ttl);
      }
      ctx.globalCompositeOperation = "source-over";
      rafId = requestAnimationFrame(step);
    }

    function start() { if (rafId === null) rafId = requestAnimationFrame(step); }
    function stop() { if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null; } }

    window.addEventListener("resize", resize);
    document.addEventListener("visibilitychange", function () {
      if (document.hidden) stop(); else start();
    });

    resize();
    step();  // draw one frame immediately, then let rAF continue the loop
    start();
  }

  /* ----- Footer year ----- */
  var yearEl = document.getElementById("year");
  if (yearEl) {
    yearEl.textContent = String(new Date().getFullYear());
  }
})();
